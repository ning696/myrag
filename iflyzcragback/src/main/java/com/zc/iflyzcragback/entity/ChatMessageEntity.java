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
@TableName("chat_messages")
/**
 * 聊天消息实体。
 *
 * <p>保存用户和助手的每一条消息。助手消息还会记录回答模式、置信度、耗时等调试信息。</p>
 */
public class ChatMessageEntity {

    @TableId(type = IdType.AUTO)
    /** 消息主键 ID。 */
    private Long id;

    /** 所属会话 ID。 */
    private String sessionId;
    /** 消息角色：user 或 assistant。 */
    private String role;
    /** 消息正文。 */
    private String content;
    /** 上下文字段，预留给后续扩展。 */
    private String context;
    /** 来源文档字段，预留给保存引用信息。 */
    private String sourceDocuments;
    /** 使用的工具名称。 */
    private String toolUsed;
    /** 使用的技能名称，预留给多技能系统。 */
    private String skillUsed;
    /** 本次消耗 token 数，预留统计成本。 */
    private Integer tokensUsed;
    /** 响应耗时，单位毫秒。 */
    private Integer responseTime;
    /** 检索或回答置信度。 */
    private Double confidence;
    /** 回答模式，对应 AnswerMode。 */
    private String answerMode;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    /** 创建时间。 */
    private LocalDateTime createdAt;

    @TableLogic
    /** 逻辑删除标记。 */
    private Integer deleted;
}
