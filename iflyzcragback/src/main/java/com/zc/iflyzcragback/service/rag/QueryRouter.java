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
/**
 * 查询路由器。
 *
 * <p>智能对话系统通常不应该所有问题都走知识库。例如“你好”适合普通聊天，
 * “今天股票多少钱”需要实时数据，而“根据我上传的文档总结一下”才应该走 RAG。
 * 本类用一个轻量的大模型判断问题类型，决定后续回答链路。</p>
 */
public class QueryRouter {

    private static final String ROUTER_PROMPT = """
            你是 QueryRouter，只负责判断用户问题应该走哪条回答链路，不要回答用户问题。

            可选 route：
            1. CHAT：只有在你确定用户是在问候、寒暄、感谢、助手身份或能力说明，且不需要任何知识库依据时才选择。
            2. KB_QA：需要根据配备的知识库、文档、资料回答。
            3. TOOL_CALLING：需要模型自主调用一个或多个工具的问题，尤其是“今天/当前/现在/最新/实时”与公开搜索、时间、计算等组合场景。
            4. REALTIME_UNAVAILABLE：保留兼容旧路由，除非无法使用 TOOL_CALLING，否则不要选择。
            5. UNCLEAR：无法判断。

            判断规则：
            - 优先根据“用户问题”判断路由。
            - “路由上下文”只用于补全代词、省略或承接问题；如果用户问题本身语义完整，不要继承历史主题。
            - 如果问题要求“今天/当前/现在/实时/最新”的价格、行情、天气、新闻、汇率、股票现价、政策公告、版本发布等，优先选 TOOL_CALLING，让模型先取当前时间再搜索。
            - 如果问题问“知识库/文档/资料/上传内容/根据材料”，选 KB_QA。
            - 如果同时出现“最新/当前”和“根据文档/知识库/上传材料”，优先选 KB_QA，不要把用户私有资料问题外发搜索。
            - 事实查询、列表查询、方案查询、上下文不确定的问题，不要选 CHAT；能判断为知识库问题就选 KB_QA，否则选 UNCLEAR。
            - 只有明确不需要知识库依据的普通对话，才选 CHAT。
            - 不要因为出现“价格”就一定选实时；如果用户问“黄金价格形成机制”或“文档中黄金价格规则”，应选 KB_QA。
            - 只输出 JSON，不要输出 Markdown。

            用户问题：%s
            路由上下文（仅用于消歧，不是回答依据）：%s

            输出格式：
            {"route":"CHAT|KB_QA|TOOL_CALLING|REALTIME_UNAVAILABLE|UNCLEAR","confidence":0到1,"reason":"简短原因"}
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

    /**
     * 判断用户问题应该进入哪条链路。
     *
     * <p>路由失败时默认返回 UNCLEAR，后续编排器会把它当作知识库问答处理，
     * 这样比直接报错更友好。</p>
     */
    public RouteDecision route(String query, List<ChatMessageEntity> history) {
        if (chatModel == null) {
            return RouteDecision.fallback("ChatLanguageModel 未配置，默认进入知识库问答");
        }
        try {
            // 路由 Prompt 要求模型只输出 JSON，方便后端稳定解析。
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

    /**
     * 将模型输出的字符串安全地转换为枚举；无法识别时返回 UNCLEAR。
     */
    private QueryRoute parseRoute(String value) {
        try {
            return QueryRoute.valueOf(value == null ? "" : value.trim());
        } catch (IllegalArgumentException e) {
            return QueryRoute.UNCLEAR;
        }
    }

    /**
     * 大模型有时会把 JSON 包在 ```json 代码块里，这里去掉外层 Markdown 标记。
     */
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

    /**
     * 将置信度限制在 0 到 1 之间，避免异常输出影响后续判断。
     */
    private double clamp(double value) {
        if (value < 0) return 0;
        if (value > 1) return 1;
        return value;
    }

    /**
     * 提取最近历史作为“消歧上下文”。
     *
     * <p>这里不会把历史当作回答依据，只帮助模型理解代词或省略表达，
     * 例如“那这个怎么处理？”里的“这个”。</p>
     */
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

    /**
     * 限制历史片段长度，防止路由 Prompt 过长、成本过高。
     */
    private String truncate(String value, int maxLength) {
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    /**
     * 路由判断结果。
     *
     * @param route 目标链路
     * @param confidence 模型对路由判断的置信度
     * @param reason 简短原因，主要用于日志和排查
     */
    public record RouteDecision(QueryRoute route, double confidence, String reason) {
        public static RouteDecision fallback(String reason) {
            return new RouteDecision(QueryRoute.UNCLEAR, 0.0, reason);
        }
    }
}
