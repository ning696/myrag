package com.zc.iflyzcragback.service.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.iflyzcragback.config.RagProperties;
import com.zc.iflyzcragback.dto.CitationVO;
import com.zc.iflyzcragback.entity.ChatMessageEntity;
import com.zc.iflyzcragback.mapper.ChatMessageMapper;
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
public class RagOrchestrator {

    private final QueryRouter queryRouter;
    private final QueryRewriter queryRewriter;
    private final HybridRetriever hybridRetriever;
    private final PromptBuilder promptBuilder;
    private final StreamingChatLanguageModel chatModel;
    private final ChatMessageMapper messageMapper;
    private final RagProperties props;
    private final ObjectMapper objectMapper;

    public SseEmitter chat(String sessionId, String query, Long userId) {
        SseEmitter emitter = new SseEmitter(60_000L);
        long startTime = System.currentTimeMillis();

        new Thread(() -> {
            try {
                List<ChatMessageEntity> history = loadHistory(sessionId);
                QueryRouter.RouteDecision decision = queryRouter.route(query, history);

                if (decision.route() == QueryRoute.REALTIME_UNAVAILABLE) {
                    List<ChatMessage> messages = promptBuilder.buildRealtimeUnavailableMessages(query, history);
                    streamAndSave(emitter, sessionId, query, messages, AnswerMode.REALTIME_UNAVAILABLE,
                            null, decision.reason(), null, startTime);
                    return;
                }

                List<String> queries = queryRewriter.rewrite(query, history);
                HybridRetriever.Result retrieved = hybridRetriever.retrieve(
                        queries, userId, props.getRetrieval().getTopK());
                List<RetrievedChunk> chunks = retrieved.chunks();
                double confidence = retrieved.topVectorScore();
                AnswerMode answerMode = resolveAnswerMode(decision, retrieved);
                boolean chatOverride = decision.route() == QueryRoute.CHAT && answerMode == AnswerMode.RAG_ANSWER;

                log.info("路由决策完成 | 原始路由={} | 最终模式={} | 命中数={} | 最高向量分数={} | 是否触发CHAT覆盖={}",
                        decision.route(), answerMode, chunks.size(), confidence, chatOverride);

                if (answerMode == AnswerMode.CHAT) {
                    List<ChatMessage> messages = promptBuilder.buildChatMessages(query, history);
                    streamAndSave(emitter, sessionId, query, messages, AnswerMode.CHAT,
                            null, decision.reason(), null, startTime);
                    return;
                }

                if (answerMode == AnswerMode.NO_KB_HIT) {
                    List<ChatMessage> messages = promptBuilder.buildNoKbHitMessages(query, history);
                    streamAndSave(emitter, sessionId, query, messages, AnswerMode.NO_KB_HIT,
                            null, decision.reason(), null, startTime);
                    return;
                }

                List<ChatMessage> messages = promptBuilder.buildMessages(query, chunks, history);
                streamAndSave(emitter, sessionId, query, messages, AnswerMode.RAG_ANSWER,
                        confidence, decision.reason(), chunks, startTime);
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

    AnswerMode resolveAnswerMode(QueryRouter.RouteDecision decision, HybridRetriever.Result retrieved) {
        if (decision.route() == QueryRoute.REALTIME_UNAVAILABLE) {
            return AnswerMode.REALTIME_UNAVAILABLE;
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

    private void streamAndSave(SseEmitter emitter,
                               String sessionId,
                               String query,
                               List<ChatMessage> messages,
                               AnswerMode answerMode,
                               Double confidence,
                               String routeReason,
                               List<RetrievedChunk> chunks,
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
                    if (emitterActive.get() && chunks != null && !chunks.isEmpty()) {
                        emitter.send(SseEmitter.event().name("citations").data(
                                objectMapper.writeValueAsString(buildCitations(chunks))));
                    }
                    if (emitterActive.get()) {
                        emitter.send(SseEmitter.event().name("done").data(donePayload(answerMode, confidence, routeReason)));
                        emitter.complete();
                    }
                    saveMessage(sessionId, query, answerBuilder.toString(), startTime, confidence, answerMode);
                } catch (IOException | IllegalStateException e) {
                    emitterActive.set(false);
                    log.warn("SSE client disconnected before completion, sessionId={}", sessionId);
                    saveMessage(sessionId, query, answerBuilder.toString(), startTime, confidence, answerMode);
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

    private String donePayload(AnswerMode answerMode, Double confidence, String routeReason) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("answerMode", answerMode.name());
        payload.put("confidence", confidence);
        payload.put("routeReason", routeReason == null ? "" : routeReason);
        return objectMapper.writeValueAsString(payload);
    }

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

    private void saveMessage(String sessionId, String query, String answer,
                             long startTime, Double confidence, AnswerMode answerMode) {
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
        aiMsg.setResponseTime((int) elapsed);
        messageMapper.insert(aiMsg);

        log.info("对话已保存: sessionId={}, answerMode={}, elapsed={}ms", sessionId, answerMode, elapsed);
    }
}
