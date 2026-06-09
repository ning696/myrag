package com.zc.iflyzcragback.service.rag;

import com.zc.iflyzcragback.dto.BM25Hit;
import com.zc.iflyzcragback.mapper.DocumentChunkMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContextWindowExpander {

    private final DocumentChunkMapper chunkMapper;

    public List<RetrievedChunk> expand(List<RetrievedChunk> anchors, Long userId, int radius) {
        if (anchors == null || anchors.isEmpty()) {
            return List.of();
        }
        if (radius <= 0) {
            return anchors;
        }

        Map<String, RetrievedChunk> expanded = new LinkedHashMap<>();
        for (RetrievedChunk anchor : anchors) {
            WindowKey key = parse(anchor);
            if (key == null) {
                expanded.putIfAbsent(anchor.key(), anchor);
                continue;
            }

            int start = Math.max(0, key.chunkIndex() - radius);
            int end = key.chunkIndex() + radius;
            List<BM25Hit> window = chunkMapper.selectWindowChunks(userId, key.documentId(), start, end);
            Map<String, BM25Hit> windowByKey = new LinkedHashMap<>();
            for (BM25Hit hit : window) {
                windowByKey.put(RetrievedChunk.keyOf(hit.documentId(), hit.chunkIndex()), hit);
            }

            for (int idx = start; idx <= end; idx++) {
                String chunkKey = RetrievedChunk.keyOf(key.documentId(), idx);
                if (chunkKey.equals(anchor.key())) {
                    // 锚点 chunk 保留 rerank 后的原始 TextSegment 和分数；覆盖之前作为邻居加入的版本。
                    expanded.put(chunkKey, anchor);
                    continue;
                }
                BM25Hit hit = windowByKey.get(chunkKey);
                if (hit != null) {
                    expanded.putIfAbsent(chunkKey, toChunk(hit, userId, anchor.score()));
                }
            }
        }
        log.info("Context window expanded: anchors={}, returned={}, radius={}",
                anchors.size(), expanded.size(), radius);
        return List.copyOf(expanded.values());
    }

    private WindowKey parse(RetrievedChunk chunk) {
        String docId = chunk.segment().metadata().getString("documentId");
        String chunkIndex = chunk.segment().metadata().getString("chunkIndex");
        if (docId == null || docId.isBlank() || chunkIndex == null || chunkIndex.isBlank()) {
            return null;
        }
        try {
            return new WindowKey(Long.parseLong(docId), Integer.parseInt(chunkIndex));
        } catch (NumberFormatException e) {
            log.warn("Skip context expansion for invalid metadata: key={}, documentId={}, chunkIndex={}",
                    chunk.key(), docId, chunkIndex);
            return null;
        }
    }

    private RetrievedChunk toChunk(BM25Hit h, Long userId, double score) {
        Metadata metadata = new Metadata()
                .put("userId", userId.toString())
                .put("documentId", String.valueOf(h.documentId()))
                .put("documentName", h.documentName() == null ? "" : h.documentName())
                .put("chunkIndex", String.valueOf(h.chunkIndex()))
                .put("title", h.title() == null ? "" : h.title())
                .put("keywords", h.keywords() == null ? "" : h.keywords());
        String key = RetrievedChunk.keyOf(h.documentId(), h.chunkIndex());
        return new RetrievedChunk(TextSegment.from(h.content(), metadata), score, key);
    }

    private record WindowKey(Long documentId, int chunkIndex) {}
}
