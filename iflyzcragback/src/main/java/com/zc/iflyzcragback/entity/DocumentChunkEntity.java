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
@TableName("document_chunks")
/**
 * 文档 chunk 实体。
 *
 * <p>一篇文档会被切成多个 chunk。MySQL 保存 chunk 原文和展示信息，
 * Milvus 保存对应向量，两边通过 documentId + chunkIndex 关联。</p>
 */
public class DocumentChunkEntity {

    @TableId(type = IdType.AUTO)
    /** chunk 主键 ID。 */
    private Long id;

    /** 所属文档 ID。 */
    private Long documentId;
    /** 所属用户 ID，用于数据隔离和 BM25 检索过滤。 */
    private Long userId;
    /** chunk 在文档中的序号，从 0 开始。 */
    private Integer chunkIndex;
    /** chunk 原文内容。 */
    private String content;
    /** chunk 标题或自动提取的短标题。 */
    private String title;
    /** 关键词，通常用逗号拼接。 */
    private String keywords;
    /** 摘要字段，当前预留给后续摘要增强使用。 */
    private String summary;
    /** 向量库中的向量 ID 或定位信息。 */
    private String vectorId;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    /** 创建时间。 */
    private LocalDateTime createdAt;

    @TableLogic
    /** 逻辑删除标记。 */
    private Integer deleted;
}
