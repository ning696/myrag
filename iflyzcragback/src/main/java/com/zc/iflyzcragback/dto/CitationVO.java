package com.zc.iflyzcragback.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CitationVO {
    private Integer n;
    private Long documentId;
    private String documentName;
    private Integer chunkIndex;
    private String content;
    private Double score;
}
