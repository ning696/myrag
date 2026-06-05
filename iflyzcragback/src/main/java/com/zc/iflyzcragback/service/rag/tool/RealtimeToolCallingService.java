package com.zc.iflyzcragback.service.rag.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.iflyzcragback.config.RagProperties;
import com.zc.iflyzcragback.dto.CitationVO;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeToolCallingService {

    private final ObjectProvider<ChatLanguageModel> chatModelProvider;
    private final ToolService toolService;
    private final RagProperties props;
    private final ObjectMapper objectMapper;

    public ToolCallingResult answer(String query) {
        if (!props.getTools().isEnabled()) {
            return ToolCallingResult.unavailable("tool calling disabled");
        }
        ChatLanguageModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            return ToolCallingResult.unavailable("ChatLanguageModel not configured");
        }

        List<ManagedTool> enabledTools = toolService.enabledTools();
        if (enabledTools.isEmpty()) {
            return ToolCallingResult.unavailable("no enabled tools");
        }

        try {
            RealtimeAssistant assistant = AiServices.builder(RealtimeAssistant.class)
                    .chatLanguageModel(chatModel)
                    .tools(enabledTools.stream().map(ManagedTool::toolInstance).toList())
                    .maxSequentialToolsInvocations(Math.max(1, props.getTools().getMaxCalls()))
                    .build();

            Result<String> result = callWithTimeout(() -> assistant.answer(query));
            List<ToolExecution> executions = result.toolExecutions() == null ? List.of() : result.toolExecutions();
            if (executions.isEmpty()) {
                return ToolCallingResult.unavailable("model did not request tools");
            }

            List<WebSearchSource> webSources = collectWebSources(executions);
            Set<String> usedTools = new LinkedHashSet<>();
            for (ToolExecution execution : executions) {
                if (execution.request() != null && execution.request().name() != null) {
                    usedTools.add(execution.request().name());
                }
            }
            String answer = result.content() == null ? "" : result.content();
            log.info("Realtime tool calling finished | tools={} | webSources={}", usedTools, webSources.size());
            return ToolCallingResult.available(
                    answer,
                    buildWebCitations(webSources),
                    topWebScore(webSources),
                    new ArrayList<>(usedTools),
                    sourceDocuments(webSources)
            );
        } catch (Exception e) {
            log.warn("Realtime tool calling failed. query=\"{}\"", query, e);
            return ToolCallingResult.unavailable(e.getMessage());
        }
    }

    private Result<String> callWithTimeout(Callable<Result<String>> callable) throws Exception {
        int timeoutMs = props.getTools().getTotalTimeoutMs();
        if (timeoutMs <= 0) {
            return callable.call();
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Result<String>> future = executor.submit(callable);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new IllegalStateException("tool calling timed out after " + timeoutMs + "ms", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw new RuntimeException(cause);
        } finally {
            executor.shutdownNow();
        }
    }

    private List<WebSearchSource> collectWebSources(List<ToolExecution> executions) {
        List<WebSearchSource> sources = new ArrayList<>();
        for (ToolExecution execution : executions) {
            if (execution.request() == null || !WebSearchTool.NAME.equals(execution.request().name())) {
                continue;
            }
            String result = execution.result();
            if (result == null || result.isBlank()) {
                continue;
            }
            try {
                JsonNode root = objectMapper.readTree(result);
                JsonNode sourceNode = root.path(WebSearchTool.SOURCES_KEY);
                if (sourceNode.isArray()) {
                    sources.addAll(objectMapper.convertValue(sourceNode, new TypeReference<List<WebSearchSource>>() {}));
                }
            } catch (Exception e) {
                log.debug("Failed to parse web_search tool result for citations", e);
            }
        }
        reindexWebSources(sources);
        return sources;
    }

    private void reindexWebSources(List<WebSearchSource> sources) {
        for (int i = 0; i < sources.size(); i++) {
            sources.get(i).setIndex(i + 1);
        }
    }

    private List<CitationVO> buildWebCitations(List<WebSearchSource> sources) {
        List<CitationVO> citations = new ArrayList<>();
        for (WebSearchSource source : sources) {
            CitationVO citation = new CitationVO();
            citation.setN(source.getIndex());
            citation.setSourceType("web");
            citation.setTitle(source.getTitle());
            citation.setDocumentName(source.getTitle());
            citation.setUrl(source.getUrl());
            citation.setContent(source.getContent());
            citation.setScore(source.getScore());
            citation.setPublishedDate(source.getPublishedDate());
            citations.add(citation);
        }
        return citations;
    }

    private Double topWebScore(List<WebSearchSource> sources) {
        return sources.stream()
                .map(WebSearchSource::getScore)
                .filter(score -> score != null)
                .max(Double::compareTo)
                .orElse(null);
    }

    private String sourceDocuments(List<WebSearchSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (Exception e) {
            return null;
        }
    }

    public record ToolCallingResult(boolean available,
                                    String answer,
                                    List<CitationVO> citations,
                                    Double confidence,
                                    List<String> usedTools,
                                    String sourceDocuments,
                                    String unavailableReason) {

        public static ToolCallingResult available(String answer,
                                                  List<CitationVO> citations,
                                                  Double confidence,
                                                  List<String> usedTools,
                                                  String sourceDocuments) {
            return new ToolCallingResult(true, answer, citations, confidence, usedTools, sourceDocuments, null);
        }

        public static ToolCallingResult unavailable(String reason) {
            return new ToolCallingResult(false, "", List.of(), null, List.of(), null, reason);
        }
    }
}
