package com.zc.iflyzcragback.service.rag;

import dev.langchain4j.data.segment.TextSegment;

public record RetrievedChunk(TextSegment segment, double score, String key) {

    public static String keyOf(Long documentId, int chunkIndex) {
        return documentId + ":" + chunkIndex;
    }
}
