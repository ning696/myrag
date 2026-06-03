package com.zc.iflyzcragback.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private Retrieval retrieval = new Retrieval();
    private Chunk chunk = new Chunk();
    private Preview preview = new Preview();
    private Ingest ingest = new Ingest();
    private History history = new History();
    private QueryRewrite queryRewrite = new QueryRewrite();
    private Rerank rerank = new Rerank();
    private Embedding embedding = new Embedding();

    @Data
    public static class Retrieval {
        private int topK = 10;
        private int rerankTopK = 3;
        private double minScore = 0.6;
    }

    @Data
    public static class Chunk {
        private int size = 800;
        private int overlap = 80;
        private String defaultStrategy = "RECURSIVE";
    }

    @Data
    public static class Preview {
        private long ttlSeconds = 1800;
    }

    @Data
    public static class Ingest {
        private long progressTtlSeconds = 3600;
        private int embedBatchSize = 16;
    }

    @Data
    public static class History {
        private int maxTurns = 4;
    }

    @Data
    public static class QueryRewrite {
        private boolean enabled = false;
        private int minQueryLength = 15;
    }

    @Data
    public static class Rerank {
        private boolean enabled = false;
        private String provider = "noop";
        private String apiKey;
        private String modelName;
        private String endpoint;
        private int timeout = 5000;
    }

    @Data
    public static class Embedding {
        private String version = "dashscope-v2-1536";
    }
}
