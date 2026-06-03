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
public class HybridRetriever {

    private final VectorRetriever vectorRetriever;
    private final DocumentChunkMapper chunkMapper;
    private final RagProperties props;

    public record Result(List<RetrievedChunk> chunks, double topVectorScore) {}

    public Result retrieve(List<String> queries, Long userId, int topK) {
        int rrfK = props.getRetrieval().getRrfK();
        Map<String, Double> rrf = new HashMap<>();
        Map<String, TextSegment> pool = new LinkedHashMap<>();
        double topVectorScore = 0.0;

        for (String q : queries) {
            List<EmbeddingMatch<TextSegment>> vec = vectorRetriever.search(q, userId, topK);
            if (!vec.isEmpty()) {
                topVectorScore = Math.max(topVectorScore, vec.get(0).score());
            }
            accumulateVector(vec, rrf, pool, rrfK);

            List<BM25Hit> bm = chunkMapper.bm25Search(q, userId, topK);
            accumulateBm25(bm, userId, rrf, pool, rrfK);
        }

        List<RetrievedChunk> merged = rrf.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> new RetrievedChunk(pool.get(e.getKey()), e.getValue(), e.getKey()))
                .toList();

        log.info("混合检索完成: userId={}, queries={}, candidates={}, returned={}, topVectorScore={}",
                userId, queries.size(), pool.size(), merged.size(), topVectorScore);
        return new Result(merged, topVectorScore);
    }

    private void accumulateVector(List<EmbeddingMatch<TextSegment>> matches,
                                  Map<String, Double> rrf,
                                  Map<String, TextSegment> pool,
                                  int rrfK) {
        for (int i = 0; i < matches.size(); i++) {
            TextSegment seg = matches.get(i).embedded();
            String docId = seg.metadata().getString("documentId");
            String idx = seg.metadata().getString("chunkIndex");
            if (docId == null || idx == null) continue;
            String key = docId + ":" + idx;
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
            rrf.merge(key, 1.0 / (rrfK + (i + 1)), Double::sum);
            pool.computeIfAbsent(key, k -> toSegment(h, userId));
        }
    }

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
