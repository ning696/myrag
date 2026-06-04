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
/**
 * 查询改写器。
 *
 * <p>用户的一句话可能太短、太口语，直接检索容易漏召回。查询改写会让大模型生成几个
 * 语义相同但措辞不同的问题，例如把“怎么弄”改成“操作步骤是什么”。之后每个问题都参与检索，
 * 可以提升知识库命中的概率。</p>
 */
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

    /**
     * 返回“原始问题 + 改写问题”。
     *
     * <p>如果未配置大模型、功能关闭、问题太短，直接返回原问题，保证系统仍然可用。</p>
     */
    public List<String> rewrite(String query, List<ChatMessageEntity> history) {
        RagProperties.QueryRewrite cfg = props.getQueryRewrite();
        if (chatModel == null
                || !cfg.isEnabled()
                || query == null
                || query.length() < cfg.getMinQueryLength()) {
            return List.of(query);
        }
        try {
            // 历史对话只取最近几轮，用于处理“那它呢？”这类承接问题。
            String prompt = REWRITE_TEMPLATE.formatted(formatHistory(history), query);
            String out = chatModel.chat(prompt);

            // 大模型有时会输出编号，这里做一次清洗，只保留真正的查询文本。
            List<String> rewrites = Arrays.stream(out.split("\\R"))
                    .map(s -> s.replaceFirst("^[\\d.\\-、)\\s]+", "").trim())
                    .filter(s -> !s.isEmpty())
                    .limit(3)
                    .toList();

            // LinkedHashSet 既去重，又保留顺序：原问题永远排第一。
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

    /**
     * 将最近几轮历史压缩成简单文本，避免把整段聊天记录都塞进改写 Prompt。
     */
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
