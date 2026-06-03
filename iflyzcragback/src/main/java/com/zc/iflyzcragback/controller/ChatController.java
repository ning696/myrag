package com.zc.iflyzcragback.controller;

import com.zc.iflyzcragback.common.Result;
import com.zc.iflyzcragback.dto.ChatRequest;
import com.zc.iflyzcragback.dto.MessageVO;
import com.zc.iflyzcragback.dto.SessionVO;
import com.zc.iflyzcragback.security.SecurityUtils;
import com.zc.iflyzcragback.service.ChatService;
import com.zc.iflyzcragback.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final SessionService sessionService;
    private final ChatService chatService;

    @PostMapping("/sessions")
    public Result<SessionVO> createSession(@RequestBody(required = false) Map<String, String> body) {
        Long userId = SecurityUtils.getCurrentUserId();
        String title = body != null ? body.get("title") : null;
        return Result.success(sessionService.create(userId, title));
    }

    @GetMapping("/sessions")
    public Result<List<SessionVO>> listSessions() {
        return Result.success(sessionService.list(SecurityUtils.getCurrentUserId()));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable String sessionId) {
        sessionService.delete(sessionId, SecurityUtils.getCurrentUserId());
        return Result.success();
    }

    @PatchMapping("/sessions/{sessionId}")
    public Result<Void> renameSession(@PathVariable String sessionId,
                                      @RequestBody Map<String, String> body) {
        sessionService.rename(sessionId, SecurityUtils.getCurrentUserId(), body.get("title"));
        return Result.success();
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<MessageVO>> getMessages(@PathVariable String sessionId) {
        return Result.success(sessionService.getMessages(sessionId, SecurityUtils.getCurrentUserId()));
    }

    @PostMapping("/messages/stream")
    public SseEmitter stream(@Valid @RequestBody ChatRequest req) {
        return chatService.stream(req.getSessionId(), req.getQuery(), SecurityUtils.getCurrentUserId());
    }
}
