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
public class DocumentChunkEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;
    private Long userId;
    private Integer chunkIndex;
    private String content;
    private String title;
    private String keywords;
    private String summary;
    private String vectorId;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}
