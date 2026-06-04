package com.zc.iflyzcragback.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.iflyzcragback.service.rag.QueryRoute;
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
/**
 * Tavily 联网搜索插件。
 */
public class WebSearchPlugin implements Plugin {

    private static final String SOURCES_KEY = "webSources";
    private static final String TAVILY_SEARCH_URL = "https://api.tavily.com/search";

    private final ObjectMapper objectMapper;

    @Value("${search.api-key:}")
    private String tavilyApiKey;

    @Override
    public String getName() {
        return "WebSearchPlugin";
    }

    @Override
    public String getDescription() {
        return "使用 Tavily Search API 查询实时网页信息";
    }

    @Override
    public PluginResult beforeRag(String query, PluginContext context) {
        if (context.getRoute() != QueryRoute.WEB_SEARCH) {
            return PluginResult.empty();
        }
        if (tavilyApiKey == null || tavilyApiKey.isBlank()) {
            log.warn("TAVILY_API_KEY 未配置，跳过联网搜索。");
            return PluginResult.empty();
        }

        try {
            SearchOptions options = options(context.getConfig(), query);
            long start = System.currentTimeMillis();
            List<WebSearchSource> sources = search(query, options);
            long latency = System.currentTimeMillis() - start;
            double topScore = sources.isEmpty() ? 0.0 : sources.get(0).getScore();
            log.info("Web search finished | query=\"{}\" | hits={} | topScore={} | latency={}ms",
                    query, sources.size(), topScore, latency);

            if (sources.isEmpty()) {
                return PluginResult.empty();
            }
            return PluginResult.builder()
                    .hasAnswer(true)
                    .pluginName(getName())
                    .metadata(Map.of(SOURCES_KEY, sources))
                    .build();
        } catch (Exception e) {
            log.warn("Tavily 搜索失败，降级为联网不可用。query=\"{}\"", query, e);
            return PluginResult.empty();
        }
    }

    @Override
    public PluginResult afterRag(String answer, String retrievedContext, PluginContext context) {
        return PluginResult.empty();
    }

    public static String sourcesKey() {
        return SOURCES_KEY;
    }

    private List<WebSearchSource> search(String query, SearchOptions options) throws Exception {
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
                .uri(URI.create(TAVILY_SEARCH_URL))
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

    private SearchOptions options(Map<String, Object> config, String query) {
        int maxResults = intValue(config.get("maxResults"), 5);
        String searchDepth = stringValue(config.get("searchDepth"), "basic");
        double minScore = doubleValue(config.get("minScore"), 0.5);
        String timeRange = stringValue(config.get("timeRange"), "week");
        int timeoutMs = intValue(config.get("timeoutMs"), 5000);
        boolean newsLike = query.contains("新闻") || query.contains("最新消息")
                || query.contains("发布") || query.contains("公告");
        return new SearchOptions(
                Math.max(1, Math.min(maxResults, 20)),
                searchDepth,
                Math.max(0.0, minScore),
                timeRange,
                Math.max(1000, timeoutMs),
                newsLike
        );
    }

    private int intValue(Object value, int defaultValue) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private double doubleValue(Object value, double defaultValue) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private String stringValue(Object value, String defaultValue) {
        return value == null || value.toString().isBlank() ? defaultValue : value.toString();
    }

    private record SearchOptions(int maxResults, String searchDepth, double minScore,
                                 String timeRange, int timeoutMs, boolean newsLike) {
    }
}
