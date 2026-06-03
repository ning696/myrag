package com.zc.iflyzcragback.service.rag;

import com.zc.iflyzcragback.entity.ChatMessageEntity;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class PromptBuilder {

    private static final String RAG_SYSTEM_PROMPT = """
            你是企业知识库问答助手。请严格遵循以下规则回答用户问题：

            【规则】
            1. **仅基于下方"参考资料"回答**，禁止使用资料外的知识。
            2. 每个论述必须标注来源编号，格式 `[来源 N]`。
            3. 如果参考资料**不足以回答**问题，必须明确回复："根据现有知识库，我无法回答这个问题。" 不要编造。
            4. 如果用户问题与参考资料无关，礼貌说明并建议用户提供更多信息。
            5. 答案使用中文，结构清晰，必要时分点列出。
            """;

    public String build(String query, List<EmbeddingMatch<TextSegment>> contexts, List<ChatMessageEntity> history) {
        StringBuilder sb = new StringBuilder();
        sb.append(RAG_SYSTEM_PROMPT).append("\n\n");

        sb.append("【参考资料】\n");
        for (int i = 0; i < contexts.size(); i++) {
            TextSegment seg = contexts.get(i).embedded();
            String docName = seg.metadata().getString("documentName");
            String title = seg.metadata().getString("title");
            sb.append("[来源 ").append(i + 1).append("] (文档：").append(docName);
            if (title != null && !title.isBlank()) {
                sb.append(" \"").append(title).append("\"");
            }
            sb.append(")\n").append(seg.text()).append("\n\n");
        }

        if (history != null && !history.isEmpty()) {
            sb.append("【对话历史】\n");
            for (ChatMessageEntity msg : history) {
                sb.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("【用户问题】\n").append(query).append("\n\n");
        sb.append("【你的回答】\n");

        return sb.toString();
    }
}
