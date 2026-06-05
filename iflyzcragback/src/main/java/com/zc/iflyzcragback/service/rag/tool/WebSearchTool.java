package com.zc.iflyzcragback.service.rag.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSearchTool implements ManagedTool {

    public static final String NAME = "web_search";
    public static final String SOURCES_KEY = "sources";

    private final ObjectMapper objectMapper;
    private final ToolParameterService parameterService;

    @Value("${search.api-key:}")
    private String tavilyApiKey;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String displayName() {
        return "联网搜索";
    }

    @Override
    public String description() {
        return "搜索公开网页，用于实时价格、行情、新闻、汇率、天气、政策等公开信息。";
    }

    @Override
    public boolean available() {
        String tavilyApiKey = tavilyApiKey();
        return tavilyApiKey != null && !tavilyApiKey.isBlank();
    }

    @Tool(name = NAME, value = "搜索公开网页，用于查询实时、最新、新闻、价格、政策、汇率、行情等公开信息。只接收一个必填参数 query。")
    public String webSearch(@P("搜索关键词，应包含必要日期、地点、对象和单位。") String query) {
        String tavilyApiKey = tavilyApiKey();
        if (tavilyApiKey == null || tavilyApiKey.isBlank()) {
            return "{\"success\":false,\"message\":\"联网搜索未配置 Tavily API Key\"}";
        }
        if (query == null || query.isBlank()) {
            return "{\"success\":false,\"message\":\"web_search 缺少 query 参数\"}";
        }
        try {
            SearchOptions options = options(query);
            long start = System.currentTimeMillis();
            List<WebSearchSource> sources = search(query, options, tavilyApiKey);
            long latency = System.currentTimeMillis() - start;
            double topScore = sources.isEmpty() ? 0.0 : sources.get(0).getScore();
            log.info("Web search tool finished | query=\"{}\" | hits={} | topScore={} | latency={}ms",
                    query, sources.size(), topScore, latency);

            return objectMapper.writeValueAsString(Map.of(
                    "success", true,
                    "query", query,
                    SOURCES_KEY, sources,
                    "message", sources.isEmpty() ? "没有找到满足分数阈值的联网搜索结果" : "联网搜索完成"
            ));
        } catch (Exception e) {
            log.warn("Web search tool failed. query={}", query, e);
            return "{\"success\":false,\"message\":\"联网搜索失败: " + escape(e.getMessage()) + "\"}";
        }
    }

    private List<WebSearchSource> search(String query, SearchOptions options, String tavilyApiKey) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);
        body.put("search_depth", options.searchDepth());
        body.put("max_results", options.maxResults());
        body.put("include_answer", false);
        body.put("include_raw_content", false);
        body.put("include_usage", true);
        if (options.newsLike()) {
            body.put("topic", "news");
            body.put("time_range", options.timeRange());
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(options.timeoutMs()))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(options.endpoint()))
                .timeout(Duration.ofMillis(options.timeoutMs()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + tavilyApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Tavily HTTP " + response.statusCode() + ": " + response.body());
        }

        JsonNode results = objectMapper.readTree(response.body()).path("results");
        List<WebSearchSource> sources = new ArrayList<>();
        int index = 1;
        if (results.isArray()) {
            for (JsonNode item : results) {
                double score = item.path("score").asDouble(0.0);
                if (score < options.minScore()) {
                    continue;
                }
                sources.add(new WebSearchSource(
                        index++,
                        item.path("title").asText(""),
                        item.path("url").asText(""),
                        item.path("content").asText(""),
                        score,
                        item.path("published_date").asText(null)
                ));
                if (sources.size() >= options.maxResults()) {
                    break;
                }
            }
        }
        return sources;
    }

    private SearchOptions options(String query) {
        ToolParameterService.WebSearchSettings webSearch = parameterService.webSearchSettings();
        boolean newsLike = query.contains("新闻") || query.contains("最新消息")
                || query.contains("发布") || query.contains("公告");
        return new SearchOptions(
                webSearch.endpoint(),
                Math.max(1, Math.min(webSearch.maxResults(), 20)),
                webSearch.searchDepth(),
                Math.max(0.0, webSearch.minScore()),
                webSearch.timeRange(),
                Math.max(1000, webSearch.timeoutMs()),
                newsLike
        );
    }

    private String tavilyApiKey() {
        return tavilyApiKey;
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record SearchOptions(String endpoint, int maxResults, String searchDepth, double minScore,
                                 String timeRange, int timeoutMs, boolean newsLike) {
    }
}
