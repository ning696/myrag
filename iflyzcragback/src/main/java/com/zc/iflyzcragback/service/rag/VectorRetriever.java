package com.zc.iflyzcragback.service.rag;

import com.zc.iflyzcragback.config.RagProperties;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * 向量检索器。
 *
 * <p>在 RAG 里，用户问题不能直接拿去和文档文字做普通字符串比较，而是先转成向量，
 * 再到 Milvus 里找“语义最接近”的文档片段。这个类只负责这一件事：把 query 向量化，
 * 带上用户隔离条件，按相似度从向量库取回候选 chunk。</p>
 */
public class VectorRetriever {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingService embeddingService;
    private final RagProperties props;

    /**
     * 根据用户问题执行向量相似度检索。
     *
     * @param query 用户原始问题或改写后的问题
     * @param userId 当前登录用户 ID，用于保证只能检索自己的文档
     * @param topK 最多返回多少个候选片段
     */
    public List<EmbeddingMatch<TextSegment>> search(String query, Long userId, int topK) {
        // 第一步：把自然语言问题转成 embedding 向量，后续才能和 Milvus 中的文档向量比较。
        Embedding queryEmbedding = embeddingService.embed(query);

        // 第二步：强制按 userId 过滤，避免一个用户问问题时命中另一个用户上传的文档。
        Filter userFilter = metadataKey("userId").isEqualTo(userId.toString());

        // 第三步：设置检索参数。minScore 是相似度下限，低于该分数的 chunk 会被丢弃，
        // 这样可以减少“没找到依据但硬塞给大模型”的幻觉风险。
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(topK)
                .minScore(props.getRetrieval().getMinScore())
                .filter(userFilter)
                .build();

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);

        // 记录命中数量和最高分，后续排查“为什么没回答出来”时非常关键。
        log.info("向量检索完成: userId={}, query=\"{}\", hits={}, topScore={}",
                userId, query, result.matches().size(),
                result.matches().isEmpty() ? 0 : result.matches().get(0).score());

        return result.matches();
    }
}
