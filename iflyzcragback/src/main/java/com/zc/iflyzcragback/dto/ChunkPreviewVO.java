package com.zc.iflyzcragback.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChunkPreviewVO {
    private Integer chunkIndex;
    private String content;
    private String title;
    private List<String> keywords;
}
