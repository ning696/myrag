package com.zc.iflyzcragback.service;

import com.zc.iflyzcragback.service.rag.RagOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
/**
 * 对话业务门面。
 *
 * <p>当前实现主要把 Controller 请求转交给 RagOrchestrator。
 * 单独保留这个 Service，后续如果要加限流、审计、敏感词检查，可以放在这里。</p>
 */
public class ChatService {

    private final RagOrchestrator orchestrator;

    /**
     * 发起一次流式智能问答。
     */
    public SseEmitter stream(String sessionId, String query, Long userId) {
        return orchestrator.chat(sessionId, query, userId);
    }
}
