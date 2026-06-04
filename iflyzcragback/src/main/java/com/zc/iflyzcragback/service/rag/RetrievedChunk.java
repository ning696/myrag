package com.zc.iflyzcragback.service.rag;

import dev.langchain4j.data.segment.TextSegment;

/**
 * 检索结果片段。
 *
 * @param segment 真实文本和 metadata
 * @param score 融合后的检索分数
 * @param key documentId:chunkIndex 形式的唯一键
 */
public record RetrievedChunk(TextSegment segment, double score, String key) {

    /**
     * 统一生成 chunk 唯一键，方便向量检索和 BM25 检索结果去重融合。
     */
    public static String keyOf(Long documentId, int chunkIndex) {
        return documentId + ":" + chunkIndex;
    }
}
