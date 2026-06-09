package com.zc.iflyzcragback.service.rag;

import com.zc.iflyzcragback.config.RagProperties;
import com.zc.iflyzcragback.dto.BM25Hit;
import com.zc.iflyzcragback.mapper.DocumentChunkMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * 混合检索器。
 *
 * <p>单纯向量检索擅长语义相似，例如“怎么登录”和“登录步骤”；
 * BM25 关键词检索擅长精确词匹配，例如产品型号、报错码、人名。这里把两路结果合并，
 * 让知识库问答既能理解语义，也不容易漏掉关键字完全命中的文档。</p>
 */
public class HybridRetriever {

    private final VectorRetriever vectorRetriever;
    private final DocumentChunkMapper chunkMapper;
    private final RerankerService rerankerService;
    private final ContextWindowExpander contextWindowExpander;
    private final RagProperties props;

    /**
     * 混合检索的最终输出。
     *
     * @param chunks 经过 RRF 融合排序后的片段
     * @param topVectorScore 向量检索最高分，用于判断是否有足够强的知识库依据
     */
    public record Result(List<RetrievedChunk> chunks, double topVectorScore) {}

    /**
     * 对一组查询执行混合检索。
     *
     * <p>queries 通常包含“原问题 + 若干个改写问题”。每个问题都会分别跑向量检索和 BM25，
     * 再通过 RRF（Reciprocal Rank Fusion，倒数排名融合）把多个排名列表合成一个总排名。</p>
     */
    public Result retrieve(List<String> queries, Long userId, int topK) {
        List<String> safeQueries = queries == null ? List.of() : queries;
        int rrfK = props.getRetrieval().getRrfK();
        // key 是 documentId:chunkIndex，value 是该 chunk 的融合分数。
        Map<String, Double> rrf = new HashMap<>();
        // pool 保存真正要交给大模型的文本片段，避免同一个 chunk 被多次加入。
        Map<String, TextSegment> pool = new LinkedHashMap<>();
        double topVectorScore = 0.0;

        for (String q : safeQueries) {
            // 1. 语义检索：适合“意思相近但措辞不同”的问题。
            List<EmbeddingMatch<TextSegment>> vec = vectorRetriever.search(q, userId, topK);
            if (!vec.isEmpty()) {
                topVectorScore = Math.max(topVectorScore, vec.get(0).score());
            }
            accumulateVector(vec, rrf, pool, rrfK);

            // 2. 关键词检索：适合编号、术语、专有名词等必须字面命中的问题。
            List<BM25Hit> bm = chunkMapper.bm25Search(q, userId, topK);
            accumulateBm25(bm, userId, rrf, pool, rrfK);
        }

        // 3. 按融合分数倒序排列，只保留 topK 个粗排候选。
        List<RetrievedChunk> merged = rrf.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> new RetrievedChunk(pool.get(e.getKey()), e.getValue(), e.getKey()))
                .toList();

        String originalQuery = safeQueries.isEmpty() ? "" : safeQueries.get(0);
        int anchorTopK = Math.max(0, props.getRetrieval().getRerankTopK());
        List<RetrievedChunk> anchors = rerankerService.rerank(originalQuery, merged, anchorTopK);
        List<RetrievedChunk> expanded = contextWindowExpander.expand(
                anchors, userId, props.getRetrieval().getContextWindowSize());

        log.info("混合检索完成: userId={}, queries={}, candidates={}, anchors={}, returned={}, topVectorScore={}",
                userId, safeQueries.size(), pool.size(), anchors.size(), expanded.size(), topVectorScore);
        return new Result(expanded, topVectorScore);
    }

    private void accumulateVector(List<EmbeddingMatch<TextSegment>> matches,
                                  Map<String, Double> rrf,
                                  Map<String, TextSegment> pool,
                                  int rrfK) {
        for (int i = 0; i < matches.size(); i++) {
            TextSegment seg = matches.get(i).embedded();
            // documentId + chunkIndex 是跨 MySQL/Milvus 都能识别的 chunk 唯一键。
            String docId = seg.metadata().getString("documentId");
            String idx = seg.metadata().getString("chunkIndex");
            if (docId == null || idx == null) continue;
            String key = docId + ":" + idx;
            // RRF 只关心“排名第几”，不直接比较向量分数和 BM25 分数，避免两种分数尺度不一致。
            rrf.merge(key, 1.0 / (rrfK + (i + 1)), Double::sum);
            pool.putIfAbsent(key, seg);
        }
    }

    private void accumulateBm25(List<BM25Hit> hits, Long userId,
                                Map<String, Double> rrf,
                                Map<String, TextSegment> pool,
                                int rrfK) {
        for (int i = 0; i < hits.size(); i++) {
            BM25Hit h = hits.get(i);
            String key = RetrievedChunk.keyOf(h.documentId(), h.chunkIndex());
            // BM25 命中的 chunk 也按排名贡献 RRF 分数。
            rrf.merge(key, 1.0 / (rrfK + (i + 1)), Double::sum);
            pool.computeIfAbsent(key, k -> toSegment(h, userId));
        }
    }

    /**
     * BM25 来源是 MySQL 行数据，这里把它包装成和向量检索一致的 TextSegment，
     * 后续 PromptBuilder 就可以统一处理两种来源的结果。
     */
    private TextSegment toSegment(BM25Hit h, Long userId) {
        Metadata m = new Metadata()
                .put("userId", userId.toString())
                .put("documentId", String.valueOf(h.documentId()))
                .put("documentName", h.documentName() == null ? "" : h.documentName())
                .put("chunkIndex", String.valueOf(h.chunkIndex()))
                .put("title", h.title() == null ? "" : h.title())
                .put("keywords", h.keywords() == null ? "" : h.keywords());
        return TextSegment.from(h.content(), m);
    }
}
