package com.zc.iflyzcragback.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DocumentVO {
    private Long id;
    private String filename;
    private String fileType;
    private Long fileSize;
    private LocalDateTime uploadTime;
    private Integer chunkCount;
    private String status;
}
