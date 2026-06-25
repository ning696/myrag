package com.zc.iflyzcragback.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Slf4j
@Configuration
@RequiredArgsConstructor
/**
 * Milvus 向量库配置。
 *
 * <p>Milvus 用来存储文档 chunk 的 embedding 向量。用户提问时，系统会到这里找语义最接近的 chunk。</p>
 */
public class MilvusConfig {

    private final MilvusProperties props;
    private final Environment environment;

    @Bean
    /**
     * 创建 LangChain4j 的 EmbeddingStore Bean，供 VectorRetriever 和 DocumentService 使用。
     */
    public EmbeddingStore<TextSegment> embeddingStore() {
        log.info("Initializing Milvus collection: {} (dim={})", props.getCollectionName(), props.getDimension());
        validateCredentials();
        MilvusEmbeddingStore.Builder builder = MilvusEmbeddingStore.builder()
                .host(props.getHost())
                .port(props.getPort())
                .collectionName(props.getCollectionName())
                .dimension(props.getDimension())
                .consistencyLevel(ConsistencyLevelEnum.STRONG)
                .autoFlushOnInsert(true);
        // 如果 Milvus 开启了账号密码认证，就把凭证传给客户端。
        if (props.getUsername() != null && !props.getUsername().isBlank()) {
            builder.username(props.getUsername());
        }
        if (props.getPassword() != null && !props.getPassword().isBlank()) {
            builder.password(props.getPassword());
        }
        return builder.build();
    }
    private void validateCredentials() {
        boolean hasUsername = props.getUsername() != null && !props.getUsername().isBlank();
        boolean hasPassword = props.getPassword() != null && !props.getPassword().isBlank();
        boolean prodProfile = environment.acceptsProfiles(Profiles.of("prod"));

        if (hasUsername != hasPassword) {
            throw new IllegalStateException("Milvus username and password must be configured together");
        }
        if (prodProfile && (!hasUsername || !hasPassword)) {
            throw new IllegalStateException("MILVUS_USERNAME and MILVUS_PASSWORD must be configured in prod profile");
        }
    }
}
