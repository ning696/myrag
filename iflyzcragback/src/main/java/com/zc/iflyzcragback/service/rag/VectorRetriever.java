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
public class VectorRetriever {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingService embeddingService;
    private final RagProperties props;

    public List<EmbeddingMatch<TextSegment>> search(String query, Long userId, int topK) {
        Embedding queryEmbedding = embeddingService.embed(query);

        Filter userFilter = metadataKey("userId").isEqualTo(userId.toString());

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(topK)
                .minScore(props.getRetrieval().getMinScore())
                .filter(userFilter)
                .build();

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);

        log.info("向量检索完成: userId={}, query=\"{}\", hits={}, topScore={}",
                userId, query, result.matches().size(),
                result.matches().isEmpty() ? 0 : result.matches().get(0).score());

        return result.matches();
    }
}
