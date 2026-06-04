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
@TableName("documents")
/**
 * 文档表实体。
 *
 * <p>对应用户上传的一份原始文档。它记录文件信息、处理状态、chunk 数量和对象存储位置。</p>
 */
public class DocumentEntity {

    @TableId(type = IdType.AUTO)
    /** 文档主键 ID。 */
    private Long id;

    /** 文档所属用户 ID，用于数据隔离。 */
    private Long userId;
    /** 原始文件名。 */
    private String filename;
    /** 文件类型，例如 pdf/txt/md。 */
    private String fileType;
    /** 文件大小，单位字节。 */
    private Long fileSize;

    @TableField(value = "upload_time", fill = FieldFill.INSERT)
    /** 上传时间。 */
    private LocalDateTime uploadTime;

    /** 文档被切成的 chunk 数量。 */
    private Integer chunkCount;
    /** 处理状态，例如 processing/completed/failed。 */
    private String status;
    /** 原文件在 MinIO 中的 objectKey。 */
    private String vectorStoreId;
    /** 入库时使用的 embedding 版本。 */
    private String embeddingVersion;
    /** 处理失败时的错误信息。 */
    private String errorMessage;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    /** 创建时间。 */
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    /** 更新时间。 */
    private LocalDateTime updatedAt;

    @TableLogic
    /** 逻辑删除标记。 */
    private Integer deleted;
}
