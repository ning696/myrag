package com.zc.iflyzcragback.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
/**
 * 流式聊天请求 DTO。
 *
 * <p>前端发送用户问题时，需要告诉后端属于哪个会话。</p>
 */
public class ChatRequest {
    @NotBlank(message = "sessionId 不能为空")
    /** 会话 ID。 */
    private String sessionId;

    @NotBlank(message = "query 不能为空")
    /** 用户本轮问题。 */
    private String query;
}
