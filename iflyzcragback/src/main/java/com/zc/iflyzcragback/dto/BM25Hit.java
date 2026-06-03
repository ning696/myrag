package com.zc.iflyzcragback.dto;

import java.time.LocalDateTime;

public record BM25Hit(
        Long id,
        Long documentId,
        Long userId,
        Integer chunkIndex,
        String content,
        String title,
        String keywords,
        String summary,
        String vectorId,
        LocalDateTime createdAt,
        Integer deleted,
        String documentName,
        Double bm25Score
) {}
