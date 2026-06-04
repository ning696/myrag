package com.zc.iflyzcragback.service.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.iflyzcragback.config.RagProperties;
import com.zc.iflyzcragback.dto.CitationVO;
import com.zc.iflyzcragback.entity.ChatMessageEntity;
import com.zc.iflyzcragback.mapper.ChatMessageMapper;
import com.zc.iflyzcragback.plugin.PluginManager;
import com.zc.iflyzcragback.plugin.PluginResult;
import com.zc.iflyzcragback.plugin.WebSearchPlugin;
import com.zc.iflyzcragback.plugin.WebSearchSource;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * RAG 对话编排器。
 *
 * <p>这是智能对话的核心流程入口。它会把一次用户提问串成完整链路：
 * 读取历史 -> 判断问题类型 -> 查询改写 -> 混合检索 -> 构造 Prompt ->
 * 调用流式大模型 -> 推送 SSE -> 保存问答记录。</p>
 */
public class RagOrchestrator {

    private final QueryRouter queryRouter;
    private final QueryRewriter queryRewriter;
    private final HybridRetriever hybridRetriever;
    private final PromptBuilder promptBuilder;
    private final PluginManager pluginManager;
    private final StreamingChatLanguageModel chatModel;
    private final ChatMessageMapper messageMapper;
    private final RagProperties props;
    private final ObjectMapper objectMapper;

    /**
     * 开启一次流式对话。
     *
     * <p>SseEmitter 会让前端边接收 token 边展示答案，体验上类似 ChatGPT 打字输出。</p>
     */
    public SseEmitter chat(String sessionId, String query, Long userId) {
        SseEmitter emitter = new SseEmitter(60_000L);
        long startTime = System.currentTimeMillis();

        new Thread(() -> {
            try {
                // 1. 最近历史只用于路由、改写和上下文补全，不会无限制塞给模型。
                List<ChatMessageEntity> history = loadHistory(sessionId);
                // 2. 先判断问题类型，决定走普通聊天、知识库问答，还是提示实时能力不可用。
                QueryRouter.RouteDecision decision = queryRouter.route(query, history);

                if (decision.route() == QueryRoute.WEB_SEARCH) {
                    PluginResult pluginResult = pluginManager.executeBefore(
                            query, sessionId, userId, decision.route(), startTime);
                    List<WebSearchSource> webSources = extractWebSources(pluginResult);
                    if (!webSources.isEmpty()) {
                        List<ChatMessage> messages = promptBuilder.buildWebSearchMessages(query, webSources, history);
                        streamAndSave(emitter, sessionId, query, messages, AnswerMode.WEB_SEARCH,
                                topWebScore(webSources), decision.reason(), buildWebCitations(webSources),
                                pluginResult.getPluginName(), startTime);
                        return;
                    }

                    List<ChatMessage> messages = promptBuilder.buildRealtimeUnavailableMessages(query, history);
                    streamAndSave(emitter, sessionId, query, messages, AnswerMode.REALTIME_UNAVAILABLE,
                            null, decision.reason(), null, null, startTime);
                    return;
                }

                if (decision.route() == QueryRoute.REALTIME_UNAVAILABLE) {
                    List<ChatMessage> messages = promptBuilder.buildRealtimeUnavailableMessages(query, history);
                    streamAndSave(emitter, sessionId, query, messages, AnswerMode.REALTIME_UNAVAILABLE,
                            null, decision.reason(), null, null, startTime);
                    return;
                }

                // 3. 查询改写可以生成多个等价问法，提高检索召回率。
                List<String> queries = queryRewriter.rewrite(query, history);
                // 4. 混合检索同时使用向量相似度和 BM25 关键词检索。
                HybridRetriever.Result retrieved = hybridRetriever.retrieve(
                        queries, userId, props.getRetrieval().getTopK());
                List<RetrievedChunk> chunks = retrieved.chunks();
                double confidence = retrieved.topVectorScore();
                // 5. 根据路由判断和检索强度，决定最终回答模式。
                AnswerMode answerMode = resolveAnswerMode(decision, retrieved);
                boolean chatOverride = decision.route() == QueryRoute.CHAT && answerMode == AnswerMode.RAG_ANSWER;

                log.info("路由决策完成 | 原始路由={} | 最终模式={} | 命中数={} | 最高向量分数={} | 是否触发CHAT覆盖={}",
                        decision.route(), answerMode, chunks.size(), confidence, chatOverride);

                if (answerMode == AnswerMode.CHAT) {
                    // 普通聊天不带知识库资料，避免模型假装引用资料。
                    List<ChatMessage> messages = promptBuilder.buildChatMessages(query, history);
                    streamAndSave(emitter, sessionId, query, messages, AnswerMode.CHAT,
                            null, decision.reason(), null, null, startTime);
                    return;
                }

                if (answerMode == AnswerMode.NO_KB_HIT) {
                    // 知识库没命中可靠依据时，明确告诉模型“不能编造”。
                    List<ChatMessage> messages = promptBuilder.buildNoKbHitMessages(query, history);
                    streamAndSave(emitter, sessionId, query, messages, AnswerMode.NO_KB_HIT,
                            null, decision.reason(), null, null, startTime);
                    return;
                }

                // RAG 命中资料：把 chunk 拼入 Prompt，并要求回答引用来源。
                List<ChatMessage> messages = promptBuilder.buildMessages(query, chunks, history);
                streamAndSave(emitter, sessionId, query, messages, AnswerMode.RAG_ANSWER,
                        confidence, decision.reason(), buildCitations(chunks), null, startTime);
            } catch (Exception e) {
                log.error("RAG 对话失败", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                    emitter.completeWithError(e);
                } catch (IOException ex) {
                    log.error("SSE 异常推送失败", ex);
                }
            }
        }).start();

        return emitter;
    }

    /**
     * 加载最近若干轮历史。
     *
     * <p>数据库按时间倒序取最近消息，再反转成正常对话顺序，方便模型理解上下文。</p>
     */
    List<ChatMessageEntity> loadHistory(String sessionId) {
        List<ChatMessageEntity> history = messageMapper.selectList(
                new LambdaQueryWrapper<ChatMessageEntity>()
                        .eq(ChatMessageEntity::getSessionId, sessionId)
                        .orderByDesc(ChatMessageEntity::getCreatedAt)
                        .orderByDesc(ChatMessageEntity::getId)
                        .last("LIMIT " + (props.getHistory().getMaxTurns() * 2)));
        history = new ArrayList<>(history);
        Collections.reverse(history);
        return history;
    }

    /**
     * 根据路由结果和检索结果决定回答模式。
     *
     * <p>如果路由模型误把知识库问题判成闲聊，但向量检索分数很高，
     * 这里会覆盖为 RAG_ANSWER，避免漏答文档中的问题。</p>
     */
    AnswerMode resolveAnswerMode(QueryRouter.RouteDecision decision, HybridRetriever.Result retrieved) {
        if (decision.route() == QueryRoute.REALTIME_UNAVAILABLE) {
            return AnswerMode.REALTIME_UNAVAILABLE;
        }
        if (decision.route() == QueryRoute.WEB_SEARCH) {
            return AnswerMode.WEB_SEARCH;
        }

        List<RetrievedChunk> chunks = retrieved == null ? List.of() : retrieved.chunks();
        double topVectorScore = retrieved == null ? 0.0 : retrieved.topVectorScore();

        if (decision.route() == QueryRoute.CHAT) {
            boolean strongEvidence = !chunks.isEmpty()
                    && decision.confidence() < props.getRouting().getStrongChatConfidence()
                    && topVectorScore >= props.getRouting().getChatOverrideMinScore();
            return strongEvidence ? AnswerMode.RAG_ANSWER : AnswerMode.CHAT;
        }

        return chunks.isEmpty() ? AnswerMode.NO_KB_HIT : AnswerMode.RAG_ANSWER;
    }

    /**
     * 调用流式大模型，把 token 逐个推给前端，并在结束时保存完整问答。
     */
    private void streamAndSave(SseEmitter emitter,
                               String sessionId,
                               String query,
                               List<ChatMessage> messages,
                               AnswerMode answerMode,
                               Double confidence,
                               String routeReason,
                               List<CitationVO> citations,
                               String pluginUsed,
                               long startTime) {
        StringBuilder answerBuilder = new StringBuilder();
        AtomicBoolean emitterActive = new AtomicBoolean(true);
        emitter.onCompletion(() -> emitterActive.set(false));
        emitter.onTimeout(() -> emitterActive.set(false));
        emitter.onError(error -> emitterActive.set(false));

        ChatRequest chatRequest = ChatRequest.builder().messages(messages).build();
        chatModel.chat(chatRequest, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
                // token 是模型增量输出的一小段文本，前端收到后可以立即追加显示。
                answerBuilder.append(token);
                if (!emitterActive.get()) {
                    return;
                }
                try {
                    emitter.send(SseEmitter.event().name("token").data(token));
                } catch (IOException | IllegalStateException e) {
                    emitterActive.set(false);
                    log.warn("SSE client disconnected while sending token, sessionId={}", sessionId);
                }
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                try {
                    // 回答完成后，把引用信息单独发给前端，方便展示来源卡片。
                    if (emitterActive.get() && citations != null && !citations.isEmpty()) {
                        emitter.send(SseEmitter.event().name("citations").data(
                                objectMapper.writeValueAsString(citations)));
                    }
                    if (emitterActive.get()) {
                        emitter.send(SseEmitter.event().name("done").data(donePayload(answerMode, confidence, routeReason)));
                        emitter.complete();
                    }
                    saveMessage(sessionId, query, answerBuilder.toString(), startTime, confidence, answerMode, pluginUsed);
                } catch (IOException | IllegalStateException e) {
                    emitterActive.set(false);
                    log.warn("SSE client disconnected before completion, sessionId={}", sessionId);
                    saveMessage(sessionId, query, answerBuilder.toString(), startTime, confidence, answerMode, pluginUsed);
                }
            }

            @Override
            public void onError(Throwable error) {
                log.error("LLM generation failed", error);
                if (!emitterActive.get()) {
                    return;
                }
                try {
                    emitter.send(SseEmitter.event().name("error").data(error.getMessage()));
                    emitter.completeWithError(error);
                } catch (IOException | IllegalStateException e) {
                    emitterActive.set(false);
                    log.warn("SSE client disconnected before error event, sessionId={}", sessionId);
                }
            }
        });
    }

    /**
     * 构造 SSE done 事件载荷，告诉前端本次回答模式、置信度和路由原因。
     */
    private String donePayload(AnswerMode answerMode, Double confidence, String routeReason) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("answerMode", answerMode.name());
        payload.put("confidence", confidence);
        payload.put("routeReason", routeReason == null ? "" : routeReason);
        return objectMapper.writeValueAsString(payload);
    }

    /**
     * 将检索 chunk 转换成前端可展示的引用对象。
     */
    private List<CitationVO> buildCitations(List<RetrievedChunk> chunks) {
        List<CitationVO> citations = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            TextSegment seg = chunks.get(i).segment();
            String docId = seg.metadata().getString("documentId");
            String idx = seg.metadata().getString("chunkIndex");
            citations.add(new CitationVO(
                    i + 1,
                    docId == null ? null : Long.valueOf(docId),
                    seg.metadata().getString("documentName"),
                    idx == null ? null : Integer.valueOf(idx),
                    seg.text(),
                    chunks.get(i).score()
            ));
        }
        return citations;
    }

    @SuppressWarnings("unchecked")
    private List<WebSearchSource> extractWebSources(PluginResult result) {
        if (result == null || result.getMetadata() == null) {
            return List.of();
        }
        Object value = result.getMetadata().get(WebSearchPlugin.sourcesKey());
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(WebSearchSource.class::isInstance)
                    .map(WebSearchSource.class::cast)
                    .toList();
        }
        return List.of();
    }

    private Double topWebScore(List<WebSearchSource> sources) {
        return sources.stream()
                .map(WebSearchSource::getScore)
                .filter(score -> score != null)
                .max(Double::compareTo)
                .orElse(null);
    }

    private List<CitationVO> buildWebCitations(List<WebSearchSource> sources) {
        List<CitationVO> citations = new ArrayList<>();
        for (WebSearchSource source : sources) {
            CitationVO citation = new CitationVO();
            citation.setN(source.getIndex());
            citation.setSourceType("web");
            citation.setTitle(source.getTitle());
            citation.setDocumentName(source.getTitle());
            citation.setUrl(source.getUrl());
            citation.setContent(source.getContent());
            citation.setScore(source.getScore());
            citation.setPublishedDate(source.getPublishedDate());
            citations.add(citation);
        }
        return citations;
    }

    /**
     * 保存用户问题和助手回答，供会话历史和后续上下文使用。
     */
    private void saveMessage(String sessionId, String query, String answer,
                             long startTime, Double confidence, AnswerMode answerMode, String pluginUsed) {
        long elapsed = System.currentTimeMillis() - startTime;

        ChatMessageEntity userMsg = new ChatMessageEntity();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(query);
        messageMapper.insert(userMsg);

        ChatMessageEntity aiMsg = new ChatMessageEntity();
        aiMsg.setSessionId(sessionId);
        aiMsg.setRole("assistant");
        aiMsg.setContent(answer);
        aiMsg.setConfidence(confidence);
        aiMsg.setAnswerMode(answerMode.name());
        aiMsg.setPluginUsed(pluginUsed);
        aiMsg.setResponseTime((int) elapsed);
        messageMapper.insert(aiMsg);

        log.info("对话已保存: sessionId={}, answerMode={}, elapsed={}ms", sessionId, answerMode, elapsed);
    }
}
