package com.zc.iflyzcragback.service.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.iflyzcragback.config.RagProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class RerankerService {

    private final RagProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public RerankerService(RagProperties props, ObjectMapper objectMapper) {
        this(props, objectMapper, HttpClient.newBuilder().build());
    }

    RerankerService(RagProperties props, ObjectMapper objectMapper, HttpClient httpClient) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topN) {
        if (candidates == null || candidates.isEmpty() || topN <= 0) {
            return List.of();
        }
        if (!canUseDashscope()) {
            return fallback(candidates, topN);
        }

        try {
            RagProperties.Rerank cfg = props.getRerank();
            List<String> documents = candidates.stream()
                    .map(chunk -> chunk.segment().text())
                    .toList();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", cfg.getModelName());
            payload.put("query", query);
            payload.put("documents", documents);
            payload.put("top_n", topN);
            if (cfg.getInstruct() != null && !cfg.getInstruct().isBlank()) {
                payload.put("instruct", cfg.getInstruct());
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(cfg.getEndpoint()))
                    .timeout(Duration.ofMillis(Math.max(1, cfg.getTimeout())))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + cfg.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            long started = System.currentTimeMillis();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("DashScope rerank returned HTTP " + response.statusCode());
            }

            List<RetrievedChunk> ranked = parseRankedChunks(response.body(), candidates, topN);
            if (ranked.isEmpty()) {
                return fallback(candidates, topN);
            }
            log.info("Rerank completed: provider=dashscope, model={}, candidates={}, returned={}, latencyMs={}",
                    cfg.getModelName(), candidates.size(), ranked.size(), System.currentTimeMillis() - started);
            return ranked;
        } catch (Exception e) {
            RagProperties.Rerank cfg = props.getRerank();
            log.warn("Rerank failed, fallback to RRF order. provider={}, model={}, query=\"{}\"",
                    cfg.getProvider(), cfg.getModelName(), query, e);
            return fallback(candidates, topN);
        }
    }

    private boolean canUseDashscope() {
        RagProperties.Rerank cfg = props.getRerank();
        return cfg.isEnabled()
                && "dashscope".equalsIgnoreCase(cfg.getProvider())
                && cfg.getApiKey() != null
                && !cfg.getApiKey().isBlank()
                && cfg.getEndpoint() != null
                && !cfg.getEndpoint().isBlank()
                && cfg.getModelName() != null
                && !cfg.getModelName().isBlank();
    }

    private List<RetrievedChunk> parseRankedChunks(String body, List<RetrievedChunk> candidates, int topN) throws Exception {
        JsonNode results = objectMapper.readTree(body).path("results");
        if (!results.isArray()) {
            return List.of();
        }

        List<RetrievedChunk> ranked = new ArrayList<>();
        Set<Integer> usedIndexes = new LinkedHashSet<>();
        for (JsonNode result : results) {
            int index = result.path("index").asInt(-1);
            if (index < 0 || index >= candidates.size() || usedIndexes.contains(index)) {
                continue;
            }
            double score = result.path("relevance_score").asDouble(candidates.get(index).score());
            RetrievedChunk original = candidates.get(index);
            ranked.add(new RetrievedChunk(original.segment(), score, original.key()));
            usedIndexes.add(index);
            if (ranked.size() >= topN) {
                return ranked;
            }
        }

        for (int i = 0; i < candidates.size() && ranked.size() < topN; i++) {
            if (!usedIndexes.contains(i)) {
                ranked.add(candidates.get(i));
            }
        }
        return ranked;
    }

    private List<RetrievedChunk> fallback(List<RetrievedChunk> candidates, int topN) {
        return candidates.stream()
                .limit(topN)
                .toList();
    }
}
