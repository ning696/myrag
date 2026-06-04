package com.zc.iflyzcragback.service.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.iflyzcragback.entity.ChatMessageEntity;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class QueryRouter {

    private static final String ROUTER_PROMPT = """
            你是 QueryRouter，只负责判断用户问题应该走哪条回答链路，不要回答用户问题。

            可选 route：
            1. CHAT：只有在你确定用户是在问候、寒暄、感谢、助手身份或能力说明，且不需要任何知识库依据时才选择。
            2. KB_QA：需要根据配备的知识库、文档、资料回答。
            3. REALTIME_UNAVAILABLE：需要实时外部数据的问题，例如今天价格、当前行情、天气、新闻、实时汇率、股票现价。
            4. UNCLEAR：无法判断。

            判断规则：
            - 优先根据“用户问题”判断路由。
            - “路由上下文”只用于补全代词、省略或承接问题；如果用户问题本身语义完整，不要继承历史主题。
            - 如果问题要求“今天/当前/现在/实时”的价格、行情、天气、新闻等，选 REALTIME_UNAVAILABLE。
            - 如果问题问“知识库/文档/资料/上传内容/根据材料”，选 KB_QA。
            - 事实查询、列表查询、方案查询、上下文不确定的问题，不要选 CHAT；能判断为知识库问题就选 KB_QA，否则选 UNCLEAR。
            - 只有明确不需要知识库依据的普通对话，才选 CHAT。
            - 不要因为出现“价格”就一定选实时；如果用户问“黄金价格形成机制”或“文档中黄金价格规则”，应选 KB_QA。
            - 只输出 JSON，不要输出 Markdown。

            用户问题：%s
            路由上下文（仅用于消歧，不是回答依据）：%s

            输出格式：
            {"route":"CHAT|KB_QA|REALTIME_UNAVAILABLE|UNCLEAR","confidence":0到1,"reason":"简短原因"}
            """;

    private final ChatLanguageModel chatModel;
    private final ObjectMapper objectMapper;

    public QueryRouter(ObjectProvider<ChatLanguageModel> chatModelProvider, ObjectMapper objectMapper) {
        this.chatModel = chatModelProvider.getIfAvailable();
        this.objectMapper = objectMapper;
        if (this.chatModel == null) {
            log.warn("ChatLanguageModel bean not available, QueryRouter will fall back to KB_QA.");
        }
    }

    public RouteDecision route(String query, List<ChatMessageEntity> history) {
        if (chatModel == null) {
            return RouteDecision.fallback("ChatLanguageModel 未配置，默认进入知识库问答");
        }
        try {
            String prompt = ROUTER_PROMPT.formatted(query, formatRoutingContext(history));
            String output = chatModel.chat(prompt);
            JsonNode root = objectMapper.readTree(stripMarkdownFence(output));

            QueryRoute route = parseRoute(root.path("route").asText());
            double confidence = clamp(root.path("confidence").asDouble(0.0));
            String reason = root.path("reason").asText("");

            log.info("Query routed | route={} | confidence={} | reason={}", route, confidence, reason);
            return new RouteDecision(route, confidence, reason);
        } catch (Exception e) {
            log.warn("QueryRouter failed, fallback to KB_QA. query=\"{}\"", query, e);
            return RouteDecision.fallback("路由解析失败，默认进入知识库问答");
        }
    }

    private QueryRoute parseRoute(String value) {
        try {
            return QueryRoute.valueOf(value == null ? "" : value.trim());
        } catch (IllegalArgumentException e) {
            return QueryRoute.UNCLEAR;
        }
    }

    private String stripMarkdownFence(String output) {
        if (output == null) {
            return "{}";
        }
        String trimmed = output.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed.trim();
    }

    private double clamp(double value) {
        if (value < 0) return 0;
        if (value > 1) return 1;
        return value;
    }

    String formatRoutingContext(List<ChatMessageEntity> history) {
        if (history == null || history.isEmpty()) {
            return "(无)";
        }
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, history.size() - 6);
        for (int i = start; i < history.size(); i++) {
            ChatMessageEntity m = history.get(i);
            if ("user".equalsIgnoreCase(m.getRole())) {
                if (m.getContent() == null || m.getContent().isBlank()) continue;
                sb.append("user: ").append(truncate(m.getContent(), 120)).append("\n");
            } else if ("assistant".equalsIgnoreCase(m.getRole())
                    && m.getAnswerMode() != null
                    && !m.getAnswerMode().isBlank()) {
                sb.append("assistant_answer_mode: ").append(m.getAnswerMode()).append("\n");
            }
        }
        return sb.isEmpty() ? "(无)" : sb.toString();
    }

    private String truncate(String value, int maxLength) {
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    public record RouteDecision(QueryRoute route, double confidence, String reason) {
        public static RouteDecision fallback(String reason) {
            return new RouteDecision(QueryRoute.UNCLEAR, 0.0, reason);
        }
    }
}
