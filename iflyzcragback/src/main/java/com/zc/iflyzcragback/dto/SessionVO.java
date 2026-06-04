package com.zc.iflyzcragback.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
/**
 * 会话展示 VO。
 *
 * <p>用于前端展示会话列表。</p>
 */
public class SessionVO {
    /** 会话 ID。 */
    private String sessionId;
    /** 会话标题。 */
    private String title;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
