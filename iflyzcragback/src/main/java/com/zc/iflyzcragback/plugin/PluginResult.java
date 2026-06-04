package com.zc.iflyzcragback.plugin;

import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.Map;

@Data
@Builder
/**
 * 插件执行结果。
 *
 * <p>hasAnswer 表示插件已产出可用结果。answer 可以为空，例如 WebSearchPlugin
 * 只返回网页来源，由编排器继续交给 LLM 生成最终回答。</p>
 */
public class PluginResult {
    private boolean hasAnswer;
    private String answer;
    private String pluginName;
    private Map<String, Object> metadata;

    public static PluginResult empty() {
        return PluginResult.builder().hasAnswer(false).metadata(Collections.emptyMap()).build();
    }
}
