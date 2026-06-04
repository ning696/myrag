package com.zc.iflyzcragback.plugin;

import com.zc.iflyzcragback.service.rag.QueryRoute;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
/**
 * 插件执行上下文。
 */
public class PluginContext {
    private String sessionId;
    private Long userId;
    private QueryRoute route;
    private Map<String, Object> config;
    private long startTime;
}
