package com.zc.iflyzcragback.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequest {
    @NotBlank(message = "sessionId 不能为空")
    private String sessionId;

    @NotBlank(message = "query 不能为空")
    private String query;
}
