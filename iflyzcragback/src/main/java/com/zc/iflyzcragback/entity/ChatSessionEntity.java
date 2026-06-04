package com.zc.iflyzcragback.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_sessions")
/**
 * 聊天会话实体。
 *
 * <p>一个会话包含多条消息，前端通常把它展示为左侧的一条对话记录。</p>
 */
public class ChatSessionEntity {

    @TableId(type = IdType.INPUT)
    /** 会话 ID，使用 UUID 字符串。 */
    private String sessionId;

    /** 会话所属用户 ID。 */
    private Long userId;
    /** 会话标题。 */
    private String title;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    /** 创建时间。 */
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    /** 更新时间，用于会话列表排序。 */
    private LocalDateTime updatedAt;

    /** 会话状态，例如 active。 */
    private String status;

    @TableLogic
    /** 逻辑删除标记。 */
    private Integer deleted;
}
