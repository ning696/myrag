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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
/**
 * 对话接口控制器。
 *
 * <p>Controller 是前端请求进入后端的第一层。这里负责会话管理和流式问答入口，
 * 真正的业务逻辑会继续交给 Service 层处理。</p>
 */
public class ChatController {

    private final SessionService sessionService;
    private final ChatService chatService;

    /**
     * 创建一个新的聊天会话，类似 ChatGPT 左侧的一条新对话。
     */
    @PostMapping("/sessions")
    public Result<SessionVO> createSession(@RequestBody(required = false) Map<String, String> body) {
        Long userId = SecurityUtils.getCurrentUserId();
        String title = body != null ? body.get("title") : null;
        return Result.success(sessionService.create(userId, title));
    }

    /**
     * 查询当前用户的所有会话列表。
     */
    @GetMapping("/sessions")
    public Result<List<SessionVO>> listSessions() {
        return Result.success(sessionService.list(SecurityUtils.getCurrentUserId()));
    }

    /**
     * 删除指定会话，同时删除该会话下的消息记录。
     */
    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable String sessionId) {
        sessionService.delete(sessionId, SecurityUtils.getCurrentUserId());
        return Result.success();
    }

    /**
     * 重命名会话标题。
     */
    @PatchMapping("/sessions/{sessionId}")
    public Result<Void> renameSession(@PathVariable String sessionId,
                                      @RequestBody Map<String, String> body) {
        sessionService.rename(sessionId, SecurityUtils.getCurrentUserId(), body.get("title"));
        return Result.success();
    }

    /**
     * 查询某个会话下的历史消息。
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<MessageVO>> getMessages(@PathVariable String sessionId) {
        return Result.success(sessionService.getMessages(sessionId, SecurityUtils.getCurrentUserId()));
    }

    /**
     * 流式发送用户问题，并通过 SSE 持续返回模型输出。
     */
    @PostMapping(value = "/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatRequest req) {
        return chatService.stream(req.getSessionId(), req.getQuery(), SecurityUtils.getCurrentUserId());
    }
}
