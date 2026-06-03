package com.zc.iflyzcragback.service.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zc.iflyzcragback.config.RagProperties;
import com.zc.iflyzcragback.dto.CitationVO;
import com.zc.iflyzcragback.entity.ChatMessageEntity;
import com.zc.iflyzcragback.mapper.ChatMessageMapper;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagOrchestrator {

    private final VectorRetriever vectorRetriever;
    private final PromptBuilder promptBuilder;
    private final StreamingChatLanguageModel chatModel;
    private final ChatMessageMapper messageMapper;
    private final RagProperties props;

    public SseEmitter chat(String sessionId, String query, Long userId) {
        SseEmitter emitter = new SseEmitter(60_000L);
        long startTime = System.currentTimeMillis();

        new Thread(() -> {
            try {
                // 1. 检索历史对话（最近 N 轮）
                List<ChatMessageEntity> history = messageMapper.selectList(
                        new LambdaQueryWrapper<ChatMessageEntity>()
                                .eq(ChatMessageEntity::getSessionId, sessionId)
                                .orderByDesc(ChatMessageEntity::getCreatedAt)
                                .last("LIMIT " + (props.getHistory().getMaxTurns() * 2)));

                // 2. 向量检索
                List<EmbeddingMatch<TextSegment>> matches = vectorRetriever.search(query, userId, props.getRetrieval().getTopK());

                if (matches.isEmpty()) {
                    emitter.send(SseEmitter.event().name("token").data("根据现有知识库，我无法回答这个问题。"));
                    emitter.send(SseEmitter.event().name("done").data("{}"));
                    emitter.complete();
                    saveMessage(sessionId, query, "根据现有知识库，我无法回答这个问题。", matches, startTime, 0.0);
                    return;
                }

                // 3. 构造 prompt
                String prompt = promptBuilder.build(query, matches, history);

                // 4. 流式生成
                StringBuilder answerBuilder = new StringBuilder();
                ChatRequest chatRequest = ChatRequest.builder()
                        .messages(UserMessage.from(prompt))
                        .build();
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
                            // 5. 构造引用
                            List<CitationVO> citations = buildCitations(matches);
                            double confidence = matches.get(0).score();

                            emitter.send(SseEmitter.event().name("citations").data(citations));
                            emitter.send(SseEmitter.event().name("done").data("{\"confidence\":" + confidence + "}"));
                            emitter.complete();

                            // 6. 保存消息
                            saveMessage(sessionId, query, answerBuilder.toString(), matches, startTime, confidence);
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

    private List<CitationVO> buildCitations(List<EmbeddingMatch<TextSegment>> matches) {
        List<CitationVO> citations = new ArrayList<>();
        for (int i = 0; i < matches.size(); i++) {
            EmbeddingMatch<TextSegment> match = matches.get(i);
            TextSegment seg = match.embedded();
            citations.add(new CitationVO(
                    i + 1,
                    Long.valueOf(seg.metadata().getString("documentId")),
                    seg.metadata().getString("documentName"),
                    Integer.valueOf(seg.metadata().getString("chunkIndex")),
                    seg.text(),
                    match.score()
            ));
        }
        return citations;
    }

    private void saveMessage(String sessionId, String query, String answer,
                             List<EmbeddingMatch<TextSegment>> matches, long startTime, double confidence) {
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
