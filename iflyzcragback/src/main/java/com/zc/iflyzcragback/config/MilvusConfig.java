package com.zc.iflyzcragback.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MilvusConfig {

    private final MilvusProperties props;

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        log.info("Initializing Milvus collection: {} (dim={})", props.getCollectionName(), props.getDimension());
        MilvusEmbeddingStore.Builder builder = MilvusEmbeddingStore.builder()
                .host(props.getHost())
                .port(props.getPort())
                .collectionName(props.getCollectionName())
                .dimension(props.getDimension())
                .consistencyLevel(ConsistencyLevelEnum.STRONG)
                .autoFlushOnInsert(true);
        if (props.getUsername() != null && !props.getUsername().isBlank()) {
            builder.username(props.getUsername());
        }
        if (props.getPassword() != null && !props.getPassword().isBlank()) {
            builder.password(props.getPassword());
        }
        return builder.build();
    }
}
