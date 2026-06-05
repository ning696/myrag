package com.zc.iflyzcragback.service.rag.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.iflyzcragback.common.BizException;
import com.zc.iflyzcragback.config.RagProperties;
import com.zc.iflyzcragback.dto.ToolParamDefinition;
import com.zc.iflyzcragback.entity.ToolConfigEntity;
import com.zc.iflyzcragback.mapper.ToolConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ToolParameterService {

    public static final String GLOBAL_TOOL_NAME = "__global__";

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final Set<String> SENSITIVE_MARKERS = Set.of("apikey", "token", "secret", "password");

    private final ToolConfigMapper configMapper;
    private final ObjectMapper objectMapper;
    private final RagProperties props;

    @Value("${search.endpoint:https://api.tavily.com/search}")
    private String defaultSearchEndpoint;

    public List<ToolParamDefinition> globalDefinitions() {
        return definitions(GLOBAL_TOOL_NAME, globalSpecs());
    }

    public List<ToolParamDefinition> toolDefinitions(String toolName) {
        Map<String, ParamSpec> specs = specsFor(toolName);
        return specs.isEmpty() ? List.of() : definitions(toolName, specs);
    }

    public GlobalSettings globalSettings() {
        Map<String, Object> values = effectiveValues(GLOBAL_TOOL_NAME, globalSpecs());
        return new GlobalSettings(
                (Boolean) values.get("enabled"),
                (Integer) values.get("maxRounds"),
                (Integer) values.get("maxCalls"),
                (Integer) values.get("totalTimeoutMs")
        );
    }

    public TimeSettings timeSettings() {
        Map<String, Object> values = effectiveValues(CurrentTimeTool.NAME, timeSpecs());
        return new TimeSettings((String) values.get("defaultZone"));
    }

    public WebSearchSettings webSearchSettings() {
        Map<String, Object> values = effectiveValues(WebSearchTool.NAME, webSearchSpecs());
        return new WebSearchSettings(
                (String) values.get("endpoint"),
                (Integer) values.get("maxResults"),
                (String) values.get("searchDepth"),
                (Double) values.get("minScore"),
                (String) values.get("timeRange"),
                (Integer) values.get("timeoutMs")
        );
    }

    @Transactional
    public List<ToolParamDefinition> updateGlobalParams(Map<String, Object> params) {
        updateParams(GLOBAL_TOOL_NAME, globalSpecs(), params);
        return globalDefinitions();
    }

    @Transactional
    public List<ToolParamDefinition> updateToolParams(String toolName, Map<String, Object> params) {
        Map<String, ParamSpec> specs = specsFor(toolName);
        if (specs.isEmpty()) {
            throw new BizException("工具不支持参数配置: " + toolName);
        }
        updateParams(toolName, specs, params);
        return toolDefinitions(toolName);
    }

    private List<ToolParamDefinition> definitions(String toolName, Map<String, ParamSpec> specs) {
        Map<String, Object> overrides = readParams(toolName);
        List<ToolParamDefinition> result = new ArrayList<>();
        for (ParamSpec spec : specs.values()) {
            Object defaultValue = spec.defaultValue();
            Object value = overrides.containsKey(spec.key())
                    ? coerceStoredValue(toolName, spec, overrides.get(spec.key()))
                    : defaultValue;
            result.add(ToolParamDefinition.builder()
                    .key(spec.key())
                    .label(spec.label())
                    .type(spec.type())
                    .description(spec.description())
                    .defaultValue(defaultValue)
                    .value(value)
                    .overridden(overrides.containsKey(spec.key()))
                    .options(spec.options())
                    .min(spec.min())
                    .max(spec.max())
                    .build());
        }
        return result;
    }

    private Map<String, Object> effectiveValues(String toolName, Map<String, ParamSpec> specs) {
        Map<String, Object> overrides = readParams(toolName);
        Map<String, Object> values = new LinkedHashMap<>();
        for (ParamSpec spec : specs.values()) {
            values.put(spec.key(), overrides.containsKey(spec.key())
                    ? coerceStoredValue(toolName, spec, overrides.get(spec.key()))
                    : spec.defaultValue());
        }
        return values;
    }

    private void updateParams(String toolName, Map<String, ParamSpec> specs, Map<String, Object> params) {
        if (params == null) {
            throw new BizException("参数不能为空");
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            rejectSensitiveKey(key);
            ParamSpec spec = specs.get(key);
            if (spec == null) {
                throw new BizException("不支持的工具参数: " + key);
            }
            Object value = coerceAndValidate(spec, entry.getValue());
            if (!sameValue(value, spec.defaultValue())) {
                sanitized.put(key, value);
            }
        }

        ToolConfigEntity config = ensureParamsConfig(toolName);
        try {
            config.setParamsJson(sanitized.isEmpty() ? null : objectMapper.writeValueAsString(sanitized));
        } catch (Exception e) {
            throw new BizException("工具参数序列化失败");
        }
        configMapper.updateById(config);
    }

    private Map<String, Object> readParams(String toolName) {
        ToolConfigEntity config = selectConfig(toolName);
        if (config == null || config.getParamsJson() == null || config.getParamsJson().isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(config.getParamsJson(), MAP_TYPE);
            Map<String, Object> sanitized = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : parsed.entrySet()) {
                if (isSensitiveKey(entry.getKey())) {
                    log.warn("Ignoring sensitive-looking tool parameter key from database | toolName={} | key={}",
                            toolName, entry.getKey());
                    continue;
                }
                sanitized.put(entry.getKey(), entry.getValue());
            }
            return sanitized;
        } catch (Exception e) {
            log.warn("Ignoring invalid tool params_json | toolName={}", toolName, e);
            return Map.of();
        }
    }

    private ToolConfigEntity ensureParamsConfig(String toolName) {
        ToolConfigEntity config = selectConfig(toolName);
        if (config != null) {
            return config;
        }
        ToolConfigEntity created = new ToolConfigEntity();
        created.setToolName(toolName);
        created.setDisplayName(GLOBAL_TOOL_NAME.equals(toolName) ? "工具全局设置" : toolName);
        created.setDescription(GLOBAL_TOOL_NAME.equals(toolName) ? "模型工具调用全局参数" : "工具参数配置");
        created.setEnabled(1);
        configMapper.insert(created);
        return created;
    }

    private ToolConfigEntity selectConfig(String toolName) {
        return configMapper.selectOne(new LambdaQueryWrapper<ToolConfigEntity>()
                .eq(ToolConfigEntity::getToolName, toolName));
    }

    private Map<String, ParamSpec> specsFor(String toolName) {
        if (CurrentTimeTool.NAME.equals(toolName)) {
            return timeSpecs();
        }
        if (WebSearchTool.NAME.equals(toolName)) {
            return webSearchSpecs();
        }
        return Map.of();
    }

    private Map<String, ParamSpec> globalSpecs() {
        RagProperties.Tools tools = props.getTools();
        return ordered(
                bool("enabled", "启用工具调用", tools.isEnabled(), "是否允许模型在实时路由下调用工具"),
                integer("maxRounds", "最大决策轮数", tools.getMaxRounds(), 1, 8, "单次对话最多允许的工具决策轮数"),
                integer("maxCalls", "最大工具调用数", tools.getMaxCalls(), 1, 10, "单次对话最多执行的工具调用次数"),
                integer("totalTimeoutMs", "总超时毫秒", tools.getTotalTimeoutMs(), 1000, 600000, "一次工具调用链的总超时时间")
        );
    }

    private Map<String, ParamSpec> timeSpecs() {
        String zone = props.getTools().getTime().getDefaultZone();
        return ordered(timezone("defaultZone", "默认时区",
                zone == null || zone.isBlank() ? "Asia/Shanghai" : zone,
                "当前时间工具使用的默认 IANA 时区"));
    }

    private Map<String, ParamSpec> webSearchSpecs() {
        RagProperties.Tools.WebSearch webSearch = props.getTools().getWebSearch();
        return ordered(
                url("endpoint", "搜索接口地址", defaultSearchEndpoint, "Tavily 兼容搜索 API 地址"),
                integer("maxResults", "最大结果数", webSearch.getMaxResults(), 1, 20, "返回给模型的最多搜索结果数"),
                enumeration("searchDepth", "搜索深度", webSearch.getSearchDepth(), List.of("basic", "advanced"), "Tavily 搜索深度"),
                decimal("minScore", "最低分数", webSearch.getMinScore(), 0, 1, "低于该分数的搜索结果会被丢弃"),
                enumeration("timeRange", "新闻时间范围", webSearch.getTimeRange(), List.of("day", "week", "month", "year"), "新闻类搜索默认时间范围"),
                integer("timeoutMs", "请求超时毫秒", webSearch.getTimeoutMs(), 1000, 30000, "单次联网搜索 HTTP 请求超时")
        );
    }

    private Map<String, ParamSpec> ordered(ParamSpec... specs) {
        Map<String, ParamSpec> result = new LinkedHashMap<>();
        for (ParamSpec spec : specs) {
            result.put(spec.key(), spec);
        }
        return result;
    }

    private ParamSpec bool(String key, String label, boolean defaultValue, String description) {
        return new ParamSpec(key, label, "boolean", description, defaultValue, null, null, null);
    }

    private ParamSpec integer(String key, String label, int defaultValue, double min, double max, String description) {
        return new ParamSpec(key, label, "integer", description, defaultValue, min, max, null);
    }

    private ParamSpec decimal(String key, String label, double defaultValue, double min, double max, String description) {
        return new ParamSpec(key, label, "number", description, defaultValue, min, max, null);
    }

    private ParamSpec enumeration(String key, String label, String defaultValue, List<String> options, String description) {
        return new ParamSpec(key, label, "select", description, defaultValue, null, null, options);
    }

    private ParamSpec timezone(String key, String label, String defaultValue, String description) {
        return new ParamSpec(key, label, "timezone", description, defaultValue, null, null, null);
    }

    private ParamSpec url(String key, String label, String defaultValue, String description) {
        return new ParamSpec(key, label, "url", description, defaultValue, null, null, null);
    }

    private Object coerceAndValidate(ParamSpec spec, Object rawValue) {
        return switch (spec.type()) {
            case "boolean" -> coerceBoolean(spec.key(), rawValue);
            case "integer" -> coerceInteger(spec, rawValue);
            case "number" -> coerceDouble(spec, rawValue);
            case "select" -> coerceSelect(spec, rawValue);
            case "timezone" -> coerceTimezone(spec.key(), rawValue);
            case "url" -> coerceUrl(spec.key(), rawValue);
            default -> throw new BizException("不支持的参数类型: " + spec.type());
        };
    }

    private Object coerceStoredValue(String toolName, ParamSpec spec, Object rawValue) {
        try {
            return coerceAndValidate(spec, rawValue);
        } catch (BizException e) {
            log.warn("Ignoring invalid stored tool parameter | toolName={} | key={} | message={}",
                    toolName, spec.key(), e.getMessage());
            return spec.defaultValue();
        }
    }

    private Boolean coerceBoolean(String key, Object rawValue) {
        if (rawValue instanceof Boolean value) {
            return value;
        }
        if (rawValue instanceof String value && ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))) {
            return Boolean.parseBoolean(value);
        }
        throw new BizException(key + " 必须是布尔值");
    }

    private Integer coerceInteger(ParamSpec spec, Object rawValue) {
        int value;
        if (rawValue instanceof Number number) {
            value = number.intValue();
        } else if (rawValue instanceof String text && !text.isBlank()) {
            try {
                value = Integer.parseInt(text);
            } catch (NumberFormatException e) {
                throw new BizException(spec.key() + " 必须是整数");
            }
        } else {
            throw new BizException(spec.key() + " 必须是整数");
        }
        if (rawValue instanceof Number number && number.doubleValue() != value) {
            throw new BizException(spec.key() + " 必须是整数");
        }
        if (value < spec.min() || value > spec.max()) {
            throw new BizException(spec.key() + " 必须在 " + spec.min().intValue() + "-" + spec.max().intValue() + " 之间");
        }
        return value;
    }

    private Double coerceDouble(ParamSpec spec, Object rawValue) {
        double value;
        if (rawValue instanceof Number number) {
            value = number.doubleValue();
        } else if (rawValue instanceof String text && !text.isBlank()) {
            try {
                value = Double.parseDouble(text);
            } catch (NumberFormatException e) {
                throw new BizException(spec.key() + " 必须是数字");
            }
        } else {
            throw new BizException(spec.key() + " 必须是数字");
        }
        if (value < spec.min() || value > spec.max()) {
            throw new BizException(spec.key() + " 必须在 " + spec.min() + "-" + spec.max() + " 之间");
        }
        return value;
    }

    private String coerceSelect(ParamSpec spec, Object rawValue) {
        String value = requireText(spec.key(), rawValue);
        if (!spec.options().contains(value)) {
            throw new BizException(spec.key() + " 必须是: " + String.join(", ", spec.options()));
        }
        return value;
    }

    private String coerceTimezone(String key, Object rawValue) {
        String value = requireText(key, rawValue);
        try {
            ZoneId.of(value);
            return value;
        } catch (DateTimeException e) {
            throw new BizException(key + " 必须是合法 IANA 时区");
        }
    }

    private String coerceUrl(String key, Object rawValue) {
        String value = requireText(key, rawValue);
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException();
            }
            return value;
        } catch (Exception e) {
            throw new BizException(key + " 必须是合法 http/https URL");
        }
    }

    private String requireText(String key, Object rawValue) {
        if (rawValue instanceof String value && !value.isBlank()) {
            return value.trim();
        }
        throw new BizException(key + " 不能为空");
    }

    private void rejectSensitiveKey(String key) {
        if (isSensitiveKey(key)) {
            throw new BizException("敏感参数不能通过工具管理保存: " + key);
        }
    }

    private boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
        return SENSITIVE_MARKERS.stream().anyMatch(normalized::contains);
    }

    private boolean sameValue(Object left, Object right) {
        if (left instanceof Number l && right instanceof Number r) {
            return Double.compare(l.doubleValue(), r.doubleValue()) == 0;
        }
        return Objects.equals(left, right);
    }

    private record ParamSpec(String key,
                             String label,
                             String type,
                             String description,
                             Object defaultValue,
                             Double min,
                             Double max,
                             List<String> options) {
    }

    public record GlobalSettings(boolean enabled, int maxRounds, int maxCalls, int totalTimeoutMs) {
    }

    public record TimeSettings(String defaultZone) {
    }

    public record WebSearchSettings(String endpoint,
                                    int maxResults,
                                    String searchDepth,
                                    double minScore,
                                    String timeRange,
                                    int timeoutMs) {
    }
}
