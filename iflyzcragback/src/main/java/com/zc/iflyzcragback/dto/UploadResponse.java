package com.zc.iflyzcragback.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadResponse {
    private Long documentId;
    private ChunkParams params;
    private List<ChunkPreviewVO> chunks;
}
