# RAG 工程化构建：召回率与准确率提升指南

> **本文档用途**：本文档是 myrag 项目 RAG 系统建设的**强制性技术规范**。Claude Code 在完成任何 RAG 相关功能（文档处理、向量检索、问答生成、效果评估等）时，**必须先阅读本文档**，并按照本文档中的方案实施。
>
> **适用范围**：[iflyzcragback/src/main/java/com/zc/iflyzcragback/rag/](../iflyzcragback/src/main/java/com/zc/iflyzcragback/rag/) 下所有代码、`ChatService`、`DocumentService` 中涉及检索/生成的逻辑。
>
> **技术栈约束**：Spring Boot 3.4.1 + LangChain4j 0.36.x + Milvus 2.3+ + DashScope `text-embedding-v2`（1536 维）+ DeepSeek（默认 LLM）。所有方案必须在此栈上实现，禁止引入冲突依赖。

---

## 目录

- [一、核心原则](#一核心原则)
- [二、文档处理层（Indexing）](#二文档处理层indexing)
- [三、检索层（Retrieval）](#三检索层retrieval)
- [四、生成层（Generation）](#四生成层generation)
- [五、评估与监控](#五评估与监控)
- [六、工程化实施路线图](#六工程化实施路线图)
- [七、Claude Code 工作清单](#七claude-code-工作清单)

---

## 一、核心原则

在动手实现任何 RAG 优化前，**先确认下面 5 条原则**，违反任何一条即视为低质量实现：

1. **可观测优先**：每一次检索、每一次生成都必须打日志（query、top-score、chunk_count、latency、命中/未命中）。无日志的优化等于没做。
2. **阈值过滤是底线**：相似度低于阈值（默认 `0.65`）的 chunk **必须丢弃**，宁可返回"不知道"也不让 LLM 凭空发挥。
3. **数据隔离不可破**：所有检索都必须带 `userId` 元数据过滤（Milvus `Filter`），任何"全局检索"代码 PR 直接拒绝。
4. **答案可溯源**：LLM 输出必须包含引用（`[来源 1]`/`[doc_id:chunk_id]`），无引用 = 不可信。
5. **变更必评估**：任何检索/生成策略调整，必须在 Golden Dataset 上跑一次 `Recall@K` / `MRR` / `Faithfulness`，性能下降则回滚。

---

## 二、文档处理层（Indexing）

> 召回率的天花板由"切块质量 + 元数据丰富度 + Embedding 模型"共同决定。这三项做不好，后面所有优化都是修修补补。

### 2.1 多粒度切块策略

**禁止做法**：固定 `chunk_size=512` 一刀切。

**推荐做法**：基于语义边界的递归切块 + 滑动窗口重叠。

```java
// rag/ingestion/SmartDocumentSplitter.java
@Component
public class SmartDocumentSplitter {

    private static final int CHUNK_SIZE = 500;     // tokens
    private static final int CHUNK_OVERLAP = 80;   // 15%~20% 重叠，保留上下文

    public DocumentSplitter create() {
        // LangChain4j 的 recursive splitter：优先按段落，再按句子，最后按字符
        return DocumentSplitters.recursive(
            CHUNK_SIZE,
            CHUNK_OVERLAP,
            new HuggingFaceTokenizer()  // 或 OpenAiTokenizer，确保 token 计数准确
        );
    }
}
```

**针对不同文档类型的切块策略**（在 `DocumentService.upload` 中按 `documentType` 分发）：

| 文档类型     | 切块策略                                | chunk_size | overlap |
| ------------ | --------------------------------------- | ---------- | ------- |
| PDF（论文/报告） | 按"章节标题 → 段落"递归                 | 500        | 80      |
| Markdown     | 按 `#`/`##`/`###` 标题层级切            | 600        | 100     |
| TXT（聊天/日志） | 按时间戳/对话回合切                     | 400        | 50      |
| 表格/结构化 | **整行整表**作为一个 chunk，不切碎      | 不切       | 0       |
| 代码         | 按函数/类边界切（用 AST，禁止按字符切） | 800        | 0       |

### 2.2 元数据富化（Metadata Enrichment）

**所有写入 Milvus 的 `TextSegment` 必须携带以下元数据**，缺一不可：

```java
TextSegment segment = TextSegment.from(chunkText, Metadata.from(Map.of(
    "userId",       userId.toString(),       // 【强制】数据隔离
    "documentId",   documentId.toString(),   // 【强制】溯源
    "documentName", document.getName(),
    "documentType", document.getType(),      // pdf/md/txt
    "chunkIndex",   String.valueOf(idx),     // 在文档中的位置
    "title",        sectionTitle,            // 该 chunk 所属章节标题
    "createdAt",    Instant.now().toString(),
    "keywords",     extractKeywords(chunkText),  // 用 TF-IDF 或 KeyBERT 提取 3~5 个关键词
    "summary",      llmSummarize(chunkText)  // 可选：用便宜的小模型生成 1 句话摘要
)));
```

**为什么**：
- `title` + `keywords` 提供 BM25 检索的素材（见 [3.2 混合检索](#32-混合检索hybrid-retrieval)）
- `summary` 可作为"多向量检索"的第二个 embedding 源
- `chunkIndex` 支持"上下文窗口扩展"（命中 chunk 后自动拉取前后 chunk）

### 2.3 Embedding 模型选型

**当前默认**：DashScope `text-embedding-v2`（1536 维，中文优化）— **保留**。

**升级路径**（按优先级）：
1. **短期**：增加 `bge-large-zh-v1.5` 作为备选（本地部署，避免云端限流）
2. **中期**：用企业私有文档 + 用户问答日志微调 `bge-base-zh`，至少能涨 5%~10% Recall
3. **长期**：训练领域专属 embedding（需 ≥10 万对正样本）

**LangChain4j 切换示例**：
```java
@Configuration
public class EmbeddingConfig {

    @Bean
    @ConditionalOnProperty(name = "rag.embedding.provider", havingValue = "dashscope", matchIfMissing = true)
    public EmbeddingModel dashscopeEmbeddingModel(@Value("${dashscope.api-key}") String apiKey) {
        return QwenEmbeddingModel.builder()
            .apiKey(apiKey)
            .modelName("text-embedding-v2")
            .build();
    }

    @Bean
    @ConditionalOnProperty(name = "rag.embedding.provider", havingValue = "bge-local")
    public EmbeddingModel bgeEmbeddingModel() {
        // 通过 OpenAI 兼容协议调用本地部署的 bge-large-zh
        return OpenAiEmbeddingModel.builder()
            .baseUrl("http://localhost:8001/v1")
            .modelName("bge-large-zh-v1.5")
            .apiKey("not-needed")
            .build();
    }
}
```

> **重要**：切换 Embedding 模型后，**必须重建整个 Milvus 索引**（不同模型向量空间不兼容）。在 `application.properties` 中维护 `rag.embedding.version` 字段，切换时触发 `ReindexService.rebuildAll()`。

---

## 三、检索层（Retrieval）

### 3.1 相似度阈值过滤（必做）

**所有 `EmbeddingStore.search` 调用必须带 `minScore`**：

```java
// rag/retrieval/RagRetriever.java
@Service
public class RagRetriever {

    @Value("${rag.retrieval.min-score:0.65}")
    private double minScore;

    @Value("${rag.retrieval.top-k:10}")
    private int topK;

    public List<EmbeddingMatch<TextSegment>> retrieve(String query, Long userId) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .maxResults(topK)
            .minScore(minScore)                                // 【强制】
            .filter(metadataKey("userId").isEqualTo(userId.toString()))  // 【强制】数据隔离
            .build();

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);

        log.info("RAG retrieve | userId={} | query=\"{}\" | hits={} | topScore={}",
            userId, query, result.matches().size(),
            result.matches().isEmpty() ? 0 : result.matches().get(0).score());

        return result.matches();
    }
}
```

**配置项**（`application.properties`）：
```properties
rag.retrieval.min-score=0.65
rag.retrieval.top-k=10
rag.retrieval.rerank-top-k=5
rag.retrieval.fallback-on-empty=true   # 检索为空时是否调用 Web 搜索工具兜底
```

### 3.2 混合检索（Hybrid Retrieval）

**为什么需要**：纯向量检索对"专有名词、产品型号、人名、错误码"等**字面匹配**敏感的查询效果差。

**实现方案**：向量检索 + BM25（基于 MySQL 全文索引或 Elasticsearch）并行 → RRF 融合。

```java
// rag/retrieval/HybridRetriever.java
@Service
@RequiredArgsConstructor
public class HybridRetriever {

    private final RagRetriever vectorRetriever;
    private final BM25Retriever keywordRetriever;  // 基于 MySQL FULLTEXT 或 ES

    public List<TextSegment> retrieve(String query, Long userId, int topK) {
        // 并行两路检索，各取 top-20
        CompletableFuture<List<EmbeddingMatch<TextSegment>>> vectorFuture =
            CompletableFuture.supplyAsync(() -> vectorRetriever.retrieve(query, userId));
        CompletableFuture<List<TextSegment>> keywordFuture =
            CompletableFuture.supplyAsync(() -> keywordRetriever.search(query, userId, 20));

        CompletableFuture.allOf(vectorFuture, keywordFuture).join();

        // RRF 融合（Reciprocal Rank Fusion，k=60 是经验值）
        return ReciprocalRankFusion.merge(
            vectorFuture.join().stream().map(EmbeddingMatch::embedded).toList(),
            keywordFuture.join(),
            60,
            topK
        );
    }
}
```

**RRF 融合公式**：`score(d) = Σ 1 / (k + rank_i(d))`，其中 `rank_i` 是文档 `d` 在第 `i` 路检索中的排名。

**MySQL FULLTEXT 索引建表**（如果暂不引入 ES）：
```sql
ALTER TABLE document_chunks
  ADD FULLTEXT INDEX idx_chunk_content (content) WITH PARSER ngram;
-- ngram 解析器对中文友好；查询时用 MATCH...AGAINST...IN BOOLEAN MODE
```

### 3.3 查询改写（Query Rewriting）

**适用场景**：用户问题模糊、含代词、口语化。

**实现方式**：在检索前调用 LLM 改写。

```java
// rag/query/QueryRewriter.java
@Service
public class QueryRewriter {

    private final ChatLanguageModel rewriteModel;  // 用便宜的小模型，如 deepseek-chat
    private static final PromptTemplate REWRITE_PROMPT = PromptTemplate.from("""
        你是查询改写助手。请将用户问题改写为 3 个不同表述但语义等价的查询，用换行分隔。
        仅输出 3 行改写后的查询，不要解释。

        历史对话（最近 2 轮）：
        {{history}}

        用户问题：{{query}}
        """);

    public List<String> rewrite(String query, List<ChatMessage> history) {
        String prompt = REWRITE_PROMPT.apply(Map.of(
            "history", formatHistory(history),
            "query", query
        )).text();

        String response = rewriteModel.generate(prompt);
        List<String> rewritten = Arrays.stream(response.split("\n"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .limit(3)
            .toList();

        // 永远把原始 query 加入候选，避免改写跑偏
        List<String> all = new ArrayList<>();
        all.add(query);
        all.addAll(rewritten);
        return all;
    }
}
```

**多查询检索**：对每个改写后的 query 都做检索，结果去重后送 Reranker。

> **成本注意**：每次问答多 1 次 LLM 调用 + 多 N 次 embedding 调用。建议配置开关 `rag.query-rewrite.enabled`，仅对**复杂查询**（长度 >15 字 或 含代词）启用。

### 3.4 Reranker 二次精排（强烈推荐）

**为什么**：embedding 模型为了通用性会牺牲精度；reranker（cross-encoder）专门做"query-doc 相关性精确打分"。

**推荐模型**：
- `bge-reranker-large`（开源，本地部署）
- Cohere Rerank API（云端，付费）
- Jina Reranker（云端，有免费额度）

**集成位置**：粗排（top-50）→ 精排（top-5）→ 送 LLM。

```java
// rag/retrieval/RerankerService.java
@Service
public class RerankerService {

    private final ScoringModel rerankerModel;  // LangChain4j 0.36+ 提供 ScoringModel 接口

    public List<TextSegment> rerank(String query, List<TextSegment> candidates, int topN) {
        if (candidates.isEmpty()) return List.of();

        List<Double> scores = rerankerModel.scoreAll(candidates, query).content();

        return IntStream.range(0, candidates.size())
            .boxed()
            .sorted(Comparator.comparingDouble(scores::get).reversed())
            .limit(topN)
            .map(candidates::get)
            .toList();
    }
}
```

**完整检索流水线**：
```
用户 query
   ↓
[查询改写] → 3~4 个查询变体
   ↓
[混合检索] 每个查询 → 向量 top-20 + BM25 top-20
   ↓
[去重合并] → 候选 50~100 个
   ↓
[Reranker 精排] → top-5
   ↓
[上下文窗口扩展] → 拉取每个 chunk 的前后相邻 chunk
   ↓
送 LLM 生成
```

### 3.5 上下文窗口扩展

命中某个 chunk 后，自动拉取它在原文档中的**前 1 个 + 后 1 个 chunk**，避免切块切碎导致语义不完整。

```java
public List<TextSegment> expandContext(List<TextSegment> hits) {
    Set<String> expandedKeys = new LinkedHashSet<>();
    for (TextSegment hit : hits) {
        String docId = hit.metadata().getString("documentId");
        int idx = Integer.parseInt(hit.metadata().getString("chunkIndex"));
        expandedKeys.add(docId + ":" + (idx - 1));
        expandedKeys.add(docId + ":" + idx);
        expandedKeys.add(docId + ":" + (idx + 1));
    }
    return chunkRepository.findByKeys(expandedKeys);  // 按 docId+chunkIndex 联合主键查
}
```

---

## 四、生成层（Generation）

### 4.1 Prompt 模板规范

**所有 RAG 问答 prompt 必须遵守此模板**（放在 `rag/prompt/RagPrompts.java`）：

```java
public static final PromptTemplate RAG_ANSWER_PROMPT = PromptTemplate.from("""
    你是企业知识库问答助手。请严格遵循以下规则回答用户问题：

    【规则】
    1. **仅基于下方"参考资料"回答**，禁止使用资料外的知识。
    2. 每个论述必须标注来源编号，格式 `[来源 N]`。
    3. 如果参考资料**不足以回答**问题，必须明确回复："根据现有知识库，我无法回答这个问题。" 不要编造。
    4. 如果用户问题与参考资料无关，礼貌说明并建议用户提供更多信息。
    5. 答案使用中文，结构清晰，必要时分点列出。

    【参考资料】
    {{context}}

    【对话历史】
    {{history}}

    【用户问题】
    {{question}}

    【你的回答】
    """);
```

**`context` 拼装格式**（每个 chunk 必须可定位）：
```
[来源 1] (文档：xxx.pdf 第 3 章)
<chunk 内容>

[来源 2] (文档：yyy.md "API 设计")
<chunk 内容>
```

### 4.2 流式输出与超时控制

```java
// 配置 ChatLanguageModel
@Bean
public StreamingChatLanguageModel chatModel(@Value("${deepseek.api-key}") String apiKey) {
    return OpenAiStreamingChatModel.builder()
        .apiKey(apiKey)
        .baseUrl("https://api.deepseek.com/v1")
        .modelName("deepseek-chat")
        .temperature(0.3)              // 【重要】RAG 场景温度调低，减少幻觉
        .timeout(Duration.ofSeconds(30))
        .maxRetries(3)
        .build();
}
```

### 4.3 答案 Grounding 验证（防幻觉）

**生成后自检**：用同一个 LLM 判断答案是否完全有据。

```java
public class AnswerVerifier {

    private static final PromptTemplate VERIFY_PROMPT = PromptTemplate.from("""
        判断"答案"是否完全基于"参考资料"。只输出一个 JSON：
        {"grounded": true/false, "unsupported_claims": ["无依据的论述1", ...]}

        参考资料：{{context}}
        答案：{{answer}}
        """);

    public VerificationResult verify(String context, String answer) {
        String json = chatModel.generate(VERIFY_PROMPT.apply(Map.of(
            "context", context, "answer", answer
        )).text());
        VerificationResult result = JsonUtils.parse(json, VerificationResult.class);
        if (!result.grounded()) {
            log.warn("Hallucination detected | claims={}", result.unsupportedClaims());
            // 可选：自动重新生成 / 在响应中标红 / 仅记录监控
        }
        return result;
    }
}
```

> **延迟权衡**：每次问答多 1 次 LLM 调用，整体延迟翻倍。建议**仅在生产环境抽样 10% 验证**，或仅对低相似度（topScore < 0.75）的回答做验证。

### 4.4 引用格式化（前端展示）

后端返回的 `ChatResponse` 必须包含结构化引用：

```java
public record ChatResponse(
    String answer,                    // 含 [来源 N] 标记的纯文本
    List<Citation> citations,         // 结构化引用列表
    double confidence,                // topScore，前端用来标记"低置信度"
    long latencyMs
) {}

public record Citation(
    int index,                        // 与答案中 [来源 N] 对应
    String documentId,
    String documentName,
    String chunkPreview,              // chunk 前 200 字
    double score
) {}
```

前端可点击 `[来源 N]` 跳转到原文档预览。

---

## 五、评估与监控

### 5.1 Golden Dataset（必须建立）

**位置**：`iflyzcragback/src/test/resources/rag-eval/golden.jsonl`

**格式**：
```json
{"id": "q001", "query": "如何配置 Milvus 连接？", "relevant_doc_ids": ["doc_42", "doc_57"], "expected_answer_keywords": ["host", "port", "collection_name"]}
{"id": "q002", "query": "用户登录失败怎么排查？", "relevant_doc_ids": ["doc_88"], "expected_answer_keywords": ["JWT", "token", "401"]}
```

**规模要求**：初期 ≥ 50 条，覆盖至少 5 种问题类型（事实查询、流程性问题、对比性问题、否定性问题、多文档综合）。

### 5.2 评估指标

| 指标 | 含义 | 目标值 |
| ---- | ---- | ---- |
| **Recall@5** | top-5 检索结果中包含正确文档的比例 | ≥ 0.85 |
| **MRR** | 平均倒数排名，正确文档排名越靠前越好 | ≥ 0.70 |
| **Faithfulness** | 答案完全有据的比例（用 LLM 判定） | ≥ 0.95 |
| **Answer Relevance** | 答案与问题相关性（用 LLM 判定 0-1） | ≥ 0.80 |
| **P95 Latency** | 端到端延迟 95 分位 | ≤ 3s |

### 5.3 评估代码骨架

```java
// rag/eval/RagEvaluator.java
@Component
public class RagEvaluator {

    public EvalReport evaluate(Path goldenPath) throws IOException {
        List<TestCase> dataset = loadJsonl(goldenPath);
        double recallSum = 0, mrrSum = 0, faithSum = 0;

        for (TestCase tc : dataset) {
            List<TextSegment> retrieved = hybridRetriever.retrieve(tc.query(), tc.userId(), 5);
            Set<String> retrievedDocIds = retrieved.stream()
                .map(s -> s.metadata().getString("documentId"))
                .collect(Collectors.toSet());

            // Recall@5
            long hit = tc.relevantDocIds().stream().filter(retrievedDocIds::contains).count();
            recallSum += (double) hit / tc.relevantDocIds().size();

            // MRR
            for (int i = 0; i < retrieved.size(); i++) {
                if (tc.relevantDocIds().contains(retrieved.get(i).metadata().getString("documentId"))) {
                    mrrSum += 1.0 / (i + 1);
                    break;
                }
            }

            // Faithfulness
            String answer = chatService.answer(tc.query(), tc.userId());
            faithSum += answerVerifier.verify(retrieved, answer).grounded() ? 1.0 : 0.0;
        }

        int n = dataset.size();
        return new EvalReport(recallSum/n, mrrSum/n, faithSum/n);
    }
}
```

**触发方式**：
- 本地：`./mvnw test -Dtest=RagEvaluatorTest`
- CI：每次 PR 涉及 `rag/` 目录时自动跑，分数下降 > 3% 阻断合并

### 5.4 实时监控

通过 Spring AOP + Micrometer 上报：

```java
@Aspect
@Component
public class RagMetricsAspect {

    private final MeterRegistry registry;

    @Around("execution(* com.zc.iflyzcragback.rag.retrieval.*.retrieve(..))")
    public Object monitorRetrieve(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.nanoTime();
        try {
            Object result = pjp.proceed();
            registry.timer("rag.retrieve.latency").record(Duration.ofNanos(System.nanoTime() - start));
            if (result instanceof List<?> list) {
                registry.counter("rag.retrieve.hits", "size", String.valueOf(list.size())).increment();
            }
            return result;
        } catch (Exception e) {
            registry.counter("rag.retrieve.errors").increment();
            throw e;
        }
    }
}
```

**关键告警规则**（Prometheus）：
- `rag_retrieve_empty_rate > 0.2`（连续 5 分钟空召回率 > 20%）→ 检查 embedding 模型
- `rag_top_score_p50 < 0.6`（中位数相似度过低）→ 索引质量下降
- `rag_latency_p95 > 5s` → 性能退化

---

## 六、工程化实施路线图

### Phase 1：地基（1~2 周，必做）

- [ ] 在 `rag/` 下建立 `ingestion/`、`retrieval/`、`prompt/`、`eval/`、`monitoring/` 子包
- [ ] 实现 `SmartDocumentSplitter`（递归切块 + 元数据富化）
- [ ] 实现 `RagRetriever`（带 `minScore` + `userId` 过滤）
- [ ] 落地 `RAG_ANSWER_PROMPT` 模板
- [ ] 添加结构化日志（query/topScore/hits/latency）
- [ ] 建立 ≥50 条 Golden Dataset

### Phase 2：召回质量（2~3 周）

- [ ] 集成 BM25（MySQL FULLTEXT 起步，复杂场景升级 ES）
- [ ] 实现 `HybridRetriever` + RRF 融合
- [ ] 集成 Reranker（先用 Cohere/Jina API 验证效果，再考虑本地化）
- [ ] 实现上下文窗口扩展
- [ ] 跑通 `RagEvaluator`，建立 `Recall@5 ≥ 0.85` 基线

### Phase 3：生成质量（2 周）

- [ ] 实现查询改写（带开关，仅复杂查询启用）
- [ ] 实现 Answer Grounding 验证（抽样 10%）
- [ ] 完善引用结构化输出 + 前端跳转
- [ ] 接入 Micrometer + Prometheus + Grafana 监控

### Phase 4：持续优化（长期）

- [ ] 收集用户点踩日志 → 构建 hard negatives
- [ ] 用企业私有数据微调 embedding 模型
- [ ] A/B 测试框架，新策略灰度上线
- [ ] 定期回归评估（每周一次）

---

## 七、Claude Code 工作清单

> **以下是 Claude Code 在执行 RAG 相关任务时的强制检查项**。每完成一个相关任务，逐条核对：

### 文档处理类任务

- [ ] 是否使用了 `SmartDocumentSplitter` 而不是固定大小切块？
- [ ] 是否为每个 chunk 写入了完整的 7 项元数据（含 `userId`、`documentId`、`chunkIndex`）？
- [ ] 是否考虑了文档类型差异化处理（PDF/MD/TXT 不同策略）？

### 检索类任务

- [ ] `EmbeddingSearchRequest` 是否带 `minScore`（默认 0.65）？
- [ ] 是否带 `userId` 元数据过滤？（数据隔离不可破）
- [ ] 是否打了结构化日志（query/topScore/hits）？
- [ ] 是否走了"混合检索 → Reranker → 上下文扩展"的完整流水线？（如果项目阶段已支持）

### 生成类任务

- [ ] 是否使用了 `RAG_ANSWER_PROMPT` 模板？
- [ ] 是否要求 LLM 输出引用 `[来源 N]`？
- [ ] `temperature` 是否设置为 ≤ 0.3？
- [ ] 响应是否包含结构化 `citations` 字段供前端使用？

### 评估类任务

- [ ] 改动是否在 Golden Dataset 上跑了评估？
- [ ] `Recall@5` / `MRR` / `Faithfulness` 是否未下降？
- [ ] 是否补充了对应的 Golden 用例？

### 安全与隔离

- [ ] 所有检索是否都从 `SecurityUtils.getCurrentUserId()` 取 userId？
- [ ] API Key（DashScope、DeepSeek、Cohere 等）是否走环境变量，未硬编码？
- [ ] 用户上传的文档是否仅写入该用户自己的 Milvus partition / 带 userId 元数据？

### 性能

- [ ] 关键路径是否有降级方案（如 Reranker 超时直接走粗排）？
- [ ] 是否配置了合理的超时（embedding 5s / LLM 30s）和重试（3 次）？
- [ ] 长文档处理是否异步化（`@Async` + 任务队列），避免阻塞上传接口？

---

## 附：关键配置参考

```properties
# application.properties（RAG 段）

# Embedding
rag.embedding.provider=dashscope
rag.embedding.version=v2-1536
dashscope.api-key=${DASHSCOPE_API_KEY}

# Retrieval
rag.retrieval.min-score=0.65
rag.retrieval.top-k=10
rag.retrieval.rerank-top-k=5
rag.retrieval.fallback-on-empty=true

# Hybrid
rag.hybrid.enabled=true
rag.hybrid.bm25.weight=0.4
rag.hybrid.vector.weight=0.6
rag.hybrid.rrf.k=60

# Query Rewrite
rag.query-rewrite.enabled=true
rag.query-rewrite.min-query-length=15

# Reranker
rag.reranker.provider=cohere
rag.reranker.api-key=${COHERE_API_KEY}

# Generation
rag.generation.temperature=0.3
rag.generation.max-tokens=2048
rag.generation.timeout-seconds=30

# Verification
rag.verify.enabled=true
rag.verify.sample-rate=0.1

# Eval
rag.eval.golden-path=classpath:rag-eval/golden.jsonl
rag.eval.recall-threshold=0.85
rag.eval.mrr-threshold=0.70
rag.eval.faithfulness-threshold=0.95
```

---

**文档版本**：v1.0
**最后更新**：2026-06-03
**维护原则**：每次 RAG 重大改动后同步更新本文档，保持文档与代码一致。
