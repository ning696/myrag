package com.zc.iflyzcragback.service.rag;

import com.zc.iflyzcragback.config.RagProperties;
import com.zc.iflyzcragback.entity.ChatMessageEntity;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class QueryRewriter {

    private static final String REWRITE_TEMPLATE = """
            你是查询改写助手。请将用户问题改写为 3 个不同表述但语义等价的查询，用换行分隔。
            仅输出 3 行改写后的查询，不要解释，不要编号。

            历史对话（最近若干轮）：
            %s

            用户问题：%s
            """;

    private final ChatLanguageModel chatModel;
    private final RagProperties props;

    public QueryRewriter(ObjectProvider<ChatLanguageModel> chatModelProvider, RagProperties props) {
        this.chatModel = chatModelProvider.getIfAvailable();
        this.props = props;
        if (this.chatModel == null) {
            log.warn("ChatLanguageModel bean not available, QueryRewriter will fall back to original query.");
        }
    }

    public List<String> rewrite(String query, List<ChatMessageEntity> history) {
        RagProperties.QueryRewrite cfg = props.getQueryRewrite();
        if (chatModel == null
                || !cfg.isEnabled()
                || query == null
                || query.length() < cfg.getMinQueryLength()) {
            return List.of(query);
        }
        try {
            String prompt = REWRITE_TEMPLATE.formatted(formatHistory(history), query);
            String out = chatModel.chat(prompt);

            List<String> rewrites = Arrays.stream(out.split("\\R"))
                    .map(s -> s.replaceFirst("^[\\d.\\-、)\\s]+", "").trim())
                    .filter(s -> !s.isEmpty())
                    .limit(3)
                    .toList();

            Set<String> all = new LinkedHashSet<>();
            all.add(query);
            all.addAll(rewrites);
            List<String> result = new ArrayList<>(all);
            log.info("Query rewrite: original=\"{}\", rewrites={}", query, result.size() - 1);
            return result;
        } catch (Exception e) {
            log.warn("Query rewrite failed, fallback to original. query=\"{}\"", query, e);
            return List.of(query);
        }
    }

    private String formatHistory(List<ChatMessageEntity> history) {
        if (history == null || history.isEmpty()) {
            return "(无)";
        }
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, history.size() - 4);
        for (int i = start; i < history.size(); i++) {
            ChatMessageEntity m = history.get(i);
            sb.append(m.getRole()).append(": ").append(m.getContent()).append("\n");
        }
        return sb.toString();
    }
}
