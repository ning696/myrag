package com.zc.iflyzcragback.dto;

import java.time.LocalDateTime;

/**
 * BM25 关键词检索命中结果。
 *
 * <p>这是 MySQL 全文检索返回给 HybridRetriever 的数据结构。
 * record 是 Java 的不可变数据载体，适合表达“只读结果”。</p>
 */
public record BM25Hit(
        /** chunk 主键 ID。 */
        Long id,
        /** 所属文档 ID。 */
        Long documentId,
        /** 所属用户 ID。 */
        Long userId,
        /** chunk 序号。 */
        Integer chunkIndex,
        /** chunk 原文。 */
        String content,
        /** chunk 标题。 */
        String title,
        /** chunk 关键词。 */
        String keywords,
        /** chunk 摘要。 */
        String summary,
        /** 向量库定位 ID。 */
        String vectorId,
        /** 创建时间。 */
        LocalDateTime createdAt,
        /** 逻辑删除标记。 */
        Integer deleted,
        /** 文档名称，用于来源展示。 */
        String documentName,
        /** BM25 检索分数。 */
        Double bm25Score
) {}
