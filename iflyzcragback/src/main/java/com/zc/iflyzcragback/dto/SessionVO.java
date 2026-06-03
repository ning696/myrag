package com.zc.iflyzcragback.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SessionVO {
    private String sessionId;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
