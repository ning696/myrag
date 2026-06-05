package com.zc.iflyzcragback.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * 消息展示 VO。
 *
 * <p>返回给前端展示聊天记录。用户消息和助手消息都使用这个结构。</p>
 */
public class MessageVO {
    /** 消息 ID。 */
    private Long id;
    /** 角色：user 或 assistant。 */
    private String role;
    /** 消息正文。 */
    private String content;
    /** RAG 回答的引用来源。 */
    private List<CitationVO> citations;
    /** 回答置信度或检索最高分。 */
    private Double confidence;
    /** 回答模式。 */
    private String answerMode;
    /** 使用的 Skill 名称。 */
    private String skillUsed;
    /** 创建时间。 */
    private LocalDateTime createdAt;
}
