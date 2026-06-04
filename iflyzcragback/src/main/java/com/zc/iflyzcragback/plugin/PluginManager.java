package com.zc.iflyzcragback.plugin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.iflyzcragback.entity.PluginConfigEntity;
import com.zc.iflyzcragback.mapper.PluginConfigMapper;
import com.zc.iflyzcragback.service.rag.QueryRoute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * 插件执行管理器。
 *
 * <p>每次执行都读取数据库配置，保证启停和配置更新无需重启即可生效。</p>
 */
public class PluginManager {

    private static final int DEFAULT_TIMEOUT_MS = 5000;

    private final List<Plugin> pluginBeans;
    private final PluginConfigMapper configMapper;
    private final ObjectMapper objectMapper;

    public PluginResult executeBefore(String query, String sessionId, Long userId, QueryRoute route, long startTime) {
        for (PluginRuntime runtime : enabledPlugins("before")) {
            PluginContext context = buildContext(runtime.config(), sessionId, userId, route, startTime);
            PluginResult result = safeExecute(runtime, () -> runtime.plugin().beforeRag(query, context));
            if (result.isHasAnswer()) {
                return result;
            }
        }
        return PluginResult.empty();
    }

    public PluginResult executeAfter(String answer, String retrievedContext, String sessionId,
                                     Long userId, QueryRoute route, long startTime) {
        PluginResult latest = PluginResult.builder().hasAnswer(true).answer(answer).build();
        String currentAnswer = answer;
        for (PluginRuntime runtime : enabledPlugins("after")) {
            PluginContext context = buildContext(runtime.config(), sessionId, userId, route, startTime);
            String answerSnapshot = currentAnswer;
            PluginResult result = safeExecute(runtime,
                    () -> runtime.plugin().afterRag(answerSnapshot, retrievedContext, context));
            if (result.isHasAnswer() && result.getAnswer() != null) {
                currentAnswer = result.getAnswer();
                latest = result;
            }
        }
        return latest;
    }

    public Map<String, Plugin> registeredPlugins() {
        return pluginBeans.stream().collect(Collectors.toMap(Plugin::getName, p -> p, (a, b) -> a));
    }

    private List<PluginRuntime> enabledPlugins(String hook) {
        Map<String, Plugin> plugins = registeredPlugins();
        return configMapper.selectList(new LambdaQueryWrapper<PluginConfigEntity>()
                        .eq(PluginConfigEntity::getEnabled, 1))
                .stream()
                .filter(c -> matchesHook(c.getHookType(), hook))
                .filter(c -> plugins.containsKey(c.getPluginName()))
                .sorted(Comparator.comparingInt((PluginConfigEntity c) ->
                        c.getPriority() == null ? 0 : c.getPriority()).reversed())
                .map(c -> new PluginRuntime(plugins.get(c.getPluginName()), c))
                .toList();
    }

    private boolean matchesHook(String configuredHook, String targetHook) {
        if (configuredHook == null || configuredHook.isBlank()) {
            return true;
        }
        String normalized = configuredHook.trim().toLowerCase();
        return "both".equals(normalized) || targetHook.equals(normalized);
    }

    private PluginContext buildContext(PluginConfigEntity config, String sessionId, Long userId,
                                       QueryRoute route, long startTime) {
        return PluginContext.builder()
                .sessionId(sessionId)
                .userId(userId)
                .route(route)
                .config(parseConfig(config.getConfigJson()))
                .startTime(startTime)
                .build();
    }

    private Map<String, Object> parseConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(configJson, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("插件配置 JSON 解析失败，将使用空配置。config={}", configJson, e);
            return Map.of();
        }
    }

    private PluginResult safeExecute(PluginRuntime runtime, Supplier<PluginResult> supplier) {
        int timeoutMs = timeoutMs(runtime.config().getConfigJson());
        try {
            PluginResult result = CompletableFuture.supplyAsync(supplier)
                    .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .join();
            return result == null ? PluginResult.empty() : result;
        } catch (Exception e) {
            log.warn("插件执行失败或超时 | plugin={} | timeoutMs={}",
                    runtime.plugin().getName(), timeoutMs, e);
            return PluginResult.empty();
        }
    }

    private int timeoutMs(String configJson) {
        Object value = parseConfig(configJson).get("timeoutMs");
        if (value instanceof Number n) {
            return Math.max(500, n.intValue());
        }
        if (value instanceof String s) {
            try {
                return Math.max(500, Integer.parseInt(s));
            } catch (NumberFormatException ignored) {
                return DEFAULT_TIMEOUT_MS;
            }
        }
        return DEFAULT_TIMEOUT_MS;
    }

    private record PluginRuntime(Plugin plugin, PluginConfigEntity config) {
    }
}
