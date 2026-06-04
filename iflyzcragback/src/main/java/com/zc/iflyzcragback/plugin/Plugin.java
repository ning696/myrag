package com.zc.iflyzcragback.plugin;

/**
 * RAG 插件统一接口。
 *
 * <p>beforeRag 用于检索前拦截或补充上下文；afterRag 用于生成后增强或兜底。</p>
 */
public interface Plugin {

    String getName();

    String getDescription();

    PluginResult beforeRag(String query, PluginContext context);

    PluginResult afterRag(String answer, String retrievedContext, PluginContext context);
}
