package com.zc.iflyzcragback.service.rag;

import com.zc.iflyzcragback.entity.ChatMessageEntity;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class PromptBuilder {

    private static final String CHAT_SYSTEM_PROMPT = """
            你是 myRAG 的知识库问答助手。当前用户问题被判定为普通对话，不需要检索知识库。
            请用中文自然、简洁地回复，并遵守：
            1. 可以处理问候、感谢、身份介绍、能力说明、使用引导；
            2. 不要回答需要知识库依据的业务事实、文档内容或专业结论；
            3. 不要编造资料、价格、实时信息、政策条款、文档内容；
            4. 如果用户问题其实需要知识库或实时数据，请说明可以让用户换一种明确问题，或上传相关文档后再问；
            5. 回答保持友好，不要提及内部路由、QueryRouter、answerMode。
            """;

    private static final String RAG_SYSTEM_PROMPT = """
            你是企业知识库问答助手。请严格遵循以下规则回答用户问题：

            【规则】
            1. **仅基于下方"参考资料"回答**，禁止使用资料外的知识。
            2. 每个论述必须标注来源编号，格式 `[来源 N]`。
            3. 如果参考资料**不足以回答**问题，必须明确回复："根据现有知识库，我无法回答这个问题。" 不要编造。
            4. 如果用户问题与参考资料无关，礼貌说明并建议用户提供更多信息。
            5. 答案使用中文，结构清晰，必要时分点列出。
            """;

    public List<ChatMessage> buildChatMessages(String query, List<ChatMessageEntity> history) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(CHAT_SYSTEM_PROMPT));
        appendHistory(messages, history);
        messages.add(UserMessage.from(query));
        return messages;
    }

    private static final String NO_KB_HIT_SYSTEM_PROMPT = """
            你是知识库问答助手。用户问题没有在知识库中检索到可靠依据。
            请用中文自然回答，但必须遵守：
            1. 明确说明当前知识库没有足够依据；
            2. 不要编造事实、数字、价格、日期；
            3. 不要声称你已经查询了外部网站或实时数据；
            4. 可以建议用户上传相关文档，或启用外部数据插件后再查询。
            """;

    private static final String REALTIME_UNAVAILABLE_SYSTEM_PROMPT = """
            你是知识库问答助手。用户问题需要实时外部数据，但当前系统没有接入实时查询能力。
            请用中文自然回答，但必须遵守：
            1. 明确说明当前不能提供实时外部数据；
            2. 不要给出任何具体实时价格、行情、天气、新闻、汇率、股票现价；
            3. 不要声称你已经查询了外部网站或实时数据；
            4. 可以建议用户上传包含相关数据的文档，或启用外部数据插件后再查询。
            """;

    public List<ChatMessage> buildMessages(String query,
                                           List<RetrievedChunk> chunks,
                                           List<ChatMessageEntity> history) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(buildSystemContent(chunks)));

        appendHistory(messages, history);

        messages.add(UserMessage.from(query));
        return messages;
    }

    public List<ChatMessage> buildNoKbHitMessages(String query, List<ChatMessageEntity> history) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(NO_KB_HIT_SYSTEM_PROMPT));
        appendHistory(messages, history);
        messages.add(UserMessage.from(query));
        return messages;
    }

    public List<ChatMessage> buildRealtimeUnavailableMessages(String query, List<ChatMessageEntity> history) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(REALTIME_UNAVAILABLE_SYSTEM_PROMPT));
        appendHistory(messages, history);
        messages.add(UserMessage.from(query));
        return messages;
    }

    private void appendHistory(List<ChatMessage> messages, List<ChatMessageEntity> history) {
        if (history == null) {
            return;
        }
        for (ChatMessageEntity msg : history) {
            String role = msg.getRole();
            String content = msg.getContent();
            if (content == null || content.isBlank()) continue;
            if ("user".equalsIgnoreCase(role)) {
                messages.add(UserMessage.from(content));
            } else if ("assistant".equalsIgnoreCase(role)) {
                messages.add(AiMessage.from(content));
            }
        }
    }

    private String buildSystemContent(List<RetrievedChunk> chunks) {
        StringBuilder sb = new StringBuilder(RAG_SYSTEM_PROMPT).append("\n【参考资料】\n");
        for (int i = 0; i < chunks.size(); i++) {
            TextSegment seg = chunks.get(i).segment();
            String docName = seg.metadata().getString("documentName");
            String title = seg.metadata().getString("title");
            sb.append("[来源 ").append(i + 1).append("] (文档：")
                    .append(docName == null ? "" : docName);
            if (title != null && !title.isBlank()) {
                sb.append(" \"").append(title).append("\"");
            }
            sb.append(")\n").append(seg.text()).append("\n\n");
        }
        return sb.toString();
    }
}
