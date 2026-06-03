package com.zc.iflyzcragback.service.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagOrchestrator {

    private final QueryRewriter queryRewriter;
    private final HybridRetriever hybridRetriever;
    private final PromptBuilder promptBuilder;
    private final StreamingChatLanguageModel chatModel;
    private final ChatMessageMapper messageMapper;
    private final RagProperties props;

    public SseEmitter chat(String sessionId, String query, Long userId) {
        SseEmitter emitter = new SseEmitter(60_000L);
        long startTime = System.currentTimeMillis();

        new Thread(() -> {
            try {
                // 1. 检索历史对话（最近 N 轮），DESC + LIMIT 取最近，再反转为旧→新
                List<ChatMessageEntity> history = messageMapper.selectList(
                        new LambdaQueryWrapper<ChatMessageEntity>()
                                .eq(ChatMessageEntity::getSessionId, sessionId)
                                .orderByDesc(ChatMessageEntity::getCreatedAt)
                                .last("LIMIT " + (props.getHistory().getMaxTurns() * 2)));
                Collections.reverse(history);

                // 2. 查询改写（短 query / 关闭 / 失败时返回 [query]）
                List<String> queries = queryRewriter.rewrite(query, history);

                // 3. 混合检索（向量 + BM25 → RRF 融合）
                HybridRetriever.Result retrieved = hybridRetriever.retrieve(
                        queries, userId, props.getRetrieval().getTopK());
                List<RetrievedChunk> chunks = retrieved.chunks();
                double confidence = retrieved.topVectorScore();

                if (chunks.isEmpty()) {
                    emitter.send(SseEmitter.event().name("token").data("根据现有知识库，我无法回答这个问题。"));
                    emitter.send(SseEmitter.event().name("done").data("{}"));
                    emitter.complete();
                    saveMessage(sessionId, query, "根据现有知识库，我无法回答这个问题。", startTime, 0.0);
                    return;
                }

                // 4. 构造多消息 prompt（system + 历史 user/assistant + 当前 user）
                List<ChatMessage> messages = promptBuilder.buildMessages(query, chunks, history);

                // 5. 流式生成
                StringBuilder answerBuilder = new StringBuilder();
                ChatRequest chatRequest = ChatRequest.builder().messages(messages).build();
                chatModel.chat(chatRequest, new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String token) {
                        answerBuilder.append(token);
                        try {
                            emitter.send(SseEmitter.event().name("token").data(token));
                        } catch (IOException e) {
                            log.error("SSE 推送失败", e);
                        }
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse response) {
                        try {
                            List<CitationVO> citations = buildCitations(chunks);
                            emitter.send(SseEmitter.event().name("citations").data(citations));
                            emitter.send(SseEmitter.event().name("done").data("{\"confidence\":" + confidence + "}"));
                            emitter.complete();
                            saveMessage(sessionId, query, answerBuilder.toString(), startTime, confidence);
                        } catch (IOException e) {
                            log.error("SSE 完成失败", e);
                            emitter.completeWithError(e);
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        log.error("LLM 生成失败", error);
                        try {
                            emitter.send(SseEmitter.event().name("error").data(error.getMessage()));
                            emitter.completeWithError(error);
                        } catch (IOException e) {
                            log.error("SSE 错误推送失败", e);
                        }
                    }
                });
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
                             long startTime, double confidence) {
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
        aiMsg.setResponseTime((int) elapsed);
        messageMapper.insert(aiMsg);

        log.info("对话已保存: sessionId={}, elapsed={}ms", sessionId, elapsed);
    }
}
