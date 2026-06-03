package com.zc.iflyzcragback.service;

import com.zc.iflyzcragback.service.rag.RagOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final RagOrchestrator orchestrator;

    public SseEmitter stream(String sessionId, String query, Long userId) {
        return orchestrator.chat(sessionId, query, userId);
    }
}
