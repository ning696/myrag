package com.zc.iflyzcragback.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
/**
 * 文档列表 VO。
 *
 * <p>返回给前端展示文档列表，只包含必要的展示字段，不暴露存储密钥等内部信息。</p>
 */
public class DocumentVO {
    /** 文档 ID。 */
    private Long id;
    /** 文件名。 */
    private String filename;
    /** 文件类型。 */
    private String fileType;
    /** 文件大小，单位字节。 */
    private Long fileSize;
    /** 上传时间。 */
    private LocalDateTime uploadTime;
    /** chunk 数量。 */
    private Integer chunkCount;
    /** 文档处理状态。 */
    private String status;
}
