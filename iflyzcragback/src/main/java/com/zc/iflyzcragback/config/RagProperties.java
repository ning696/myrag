package com.zc.iflyzcragback.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rag")
/**
 * RAG 总配置属性。
 *
 * <p>对应 application.yml 中的 rag.*，集中控制检索、切块、入库、历史、查询改写等参数。
 * 这些参数会直接影响知识库问答的准确率、速度和成本。</p>
 */
public class RagProperties {

    /** 检索相关参数。 */
    private Retrieval retrieval = new Retrieval();
    /** 文档切块相关参数。 */
    private Chunk chunk = new Chunk();
    /** 切块预览缓存参数。 */
    private Preview preview = new Preview();
    /** 文档入库参数。 */
    private Ingest ingest = new Ingest();
    /** 对话历史参数。 */
    private History history = new History();
    /** 查询改写参数。 */
    private QueryRewrite queryRewrite = new QueryRewrite();
    /** 重排序参数，当前可按需扩展。 */
    private Rerank rerank = new Rerank();
    /** embedding 模型版本信息。 */
    private Embedding embedding = new Embedding();
    /** 路由和回答模式判断参数。 */
    private Routing routing = new Routing();
    /** 模型工具调用参数。 */
    private Tools tools = new Tools();

    @Data
    /** 检索参数。 */
    public static class Retrieval {
        /** 粗召回候选数量。 */
        private int topK = 10;
        /** 精排后传给大模型的数量。 */
        private int rerankTopK = 3;
        /** 向量相似度最低分数，低于该值的 chunk 会被丢弃。 */
        private double minScore = 0.6;
        /** RRF 融合排名常数，越大越弱化排名差异。 */
        private int rrfK = 60;
    }

    @Data
    /** 文档切块参数。 */
    public static class Chunk {
        /** 单个 chunk 目标 token 数。 */
        private int size = 800;
        /** 相邻 chunk 重叠 token 数，用于保留上下文。 */
        private int overlap = 80;
        /** 默认切块策略。 */
        private String defaultStrategy = "RECURSIVE";
    }

    @Data
    /** 预览缓存参数。 */
    public static class Preview {
        /** Redis 中切块预览保留时长，单位秒。 */
        private long ttlSeconds = 1800;
    }

    @Data
    /** 文档入库参数。 */
    public static class Ingest {
        /** 入库进度在 Redis 中保留时长，单位秒。 */
        private long progressTtlSeconds = 3600;
        /** 每批向量化的 chunk 数量。 */
        private int embedBatchSize = 16;
    }

    @Data
    /** 对话历史参数。 */
    public static class History {
        /** 发送给路由/模型的最近对话轮数。 */
        private int maxTurns = 4;
    }

    @Data
    /** 查询改写参数。 */
    public static class QueryRewrite {
        /** 是否启用查询改写。 */
        private boolean enabled = false;
        /** 低于该长度的问题不改写，避免短问候浪费模型调用。 */
        private int minQueryLength = 15;
    }

    @Data
    /** 重排序参数。 */
    public static class Rerank {
        /** 是否启用 rerank。 */
        private boolean enabled = false;
        /** rerank 服务提供商。 */
        private String provider = "noop";
        /** rerank API 密钥。 */
        private String apiKey;
        /** rerank 模型名称。 */
        private String modelName;
        /** rerank 服务地址。 */
        private String endpoint;
        /** rerank 请求超时时间，单位毫秒。 */
        private int timeout = 5000;
    }

    @Data
    /** embedding 参数。 */
    public static class Embedding {
        /** 当前索引使用的 embedding 版本，切换模型时应重建索引。 */
        private String version = "dashscope-v2-1536";
    }

    @Data
    /** 路由判断参数。 */
    public static class Routing {
        /** 即使路由为 CHAT，只要检索分数高于该值，也可覆盖为 RAG 回答。 */
        private double chatOverrideMinScore = 0.72;
        /** 路由模型对 CHAT 的强置信阈值，高于该值时更倾向保持闲聊。 */
        private double strongChatConfidence = 0.95;
    }

    @Data
    /** 模型工具调用参数。 */
    public static class Tools {
        /** 是否启用模型自主工具调用。 */
        private boolean enabled = true;
        /** 单次对话最多让模型做几轮工具决策。 */
        private int maxRounds = 4;
        /** 单次对话最多执行几个工具。 */
        private int maxCalls = 6;
        /** 工具调用链总超时时间，单位毫秒。 */
        private int totalTimeoutMs = 30000;
        /** 时间工具参数。 */
        private Time time = new Time();
        /** 联网搜索工具参数。 */
        private WebSearch webSearch = new WebSearch();

        @Data
        public static class Time {
            /** 默认时区。 */
            private String defaultZone = "Asia/Shanghai";
        }

        @Data
        public static class WebSearch {
            /** 默认搜索结果数量。 */
            private int maxResults = 5;
            /** Tavily 搜索深度。 */
            private String searchDepth = "basic";
            /** 搜索结果最低分。 */
            private double minScore = 0.5;
            /** 新闻类搜索默认时间范围。 */
            private String timeRange = "week";
            /** 搜索请求超时，单位毫秒。 */
            private int timeoutMs = 5000;
        }
    }
}
