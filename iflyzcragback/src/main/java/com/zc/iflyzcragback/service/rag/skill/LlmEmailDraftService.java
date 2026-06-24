package com.zc.iflyzcragback.service.rag.skill;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * 基于聊天模型的邮件草稿生成服务。
 *
 * <p>该服务只负责“生成可编辑草稿”，不负责发送邮件。真实发送动作仍由
 * {@link EmailDeliveryService} 在用户确认后执行。</p>
 *
 * <p>Prompt 中明确要求模型不要编造邮箱、时间、地点或事实；如果模型不可用或输入上下文
 * 不足，则返回空结果，让 {@link EmailSkill} 继续向用户追问。</p>
 */
public class LlmEmailDraftService implements EmailDraftService {
    private static final int MAX_SUBJECT_LENGTH = 30;

    private final ObjectProvider<ChatLanguageModel> chatModelProvider;

    @Override
    /**
     * 根据用户要点和已收集状态生成邮件主题。
     */
    public Optional<String> draftSubject(String brief, Map<String, Object> state) {
        ChatLanguageModel model = chatModelProvider.getIfAvailable();
        String context = buildContext(brief, state);
        if (model == null || context.isBlank()) {
            return Optional.empty();
        }
        try {
            String output = model.chat("""
                    你是邮件写作助手。请根据用户要点生成一个可以直接发送的中文邮件主题。
                    要求：只输出主题本身，不要解释；简洁明确；不超过 30 个中文字符；不要编造邮箱、时间、地点或事实。

                    已知信息：
                    %s
                    """.formatted(context));
            return sanitize(output, true);
        } catch (Exception e) {
            log.warn("Email subject drafting failed", e);
            return Optional.empty();
        }
    }

    @Override
    /**
     * 根据用户要点和已收集状态生成邮件正文。
     */
    public Optional<String> draftContent(String brief, Map<String, Object> state) {
        ChatLanguageModel model = chatModelProvider.getIfAvailable();
        String context = buildContext(brief, state);
        if (model == null || context.isBlank()) {
            return Optional.empty();
        }
        try {
            String output = model.chat("""
                    你是邮件写作助手。请根据用户要点生成一段可以直接发送的中文邮件正文。
                    要求：正式、礼貌、简洁；只输出正文；不要编造邮箱、时间、地点或事实；未知信息不要自行补充。

                    已知信息：
                    %s
                    """.formatted(context));
            return sanitize(output, false);
        } catch (Exception e) {
            log.warn("Email content drafting failed", e);
            return Optional.empty();
        }
    }

    /**
     * 拼装给模型的最小上下文。
     *
     * <p>这里不补充项目外事实，只把用户已经提供的信息和技能状态传给模型。</p>
     */
    private String buildContext(String brief, Map<String, Object> state) {
        StringBuilder context = new StringBuilder();
        append(context, "用户要点", brief);
        append(context, "收件人", stringValue(state.get("recipient")));
        append(context, "已有主题", stringValue(state.get("subject")));
        append(context, "已有正文", stringValue(state.get("content")));
        append(context, "补充要点", stringValue(state.get("draftBrief")));
        return context.toString().trim();
    }

    private void append(StringBuilder context, String label, String value) {
        if (value != null && !value.trim().isBlank()) {
            context.append(label).append("：").append(value.trim()).append("\n");
        }
    }

    /**
     * 清洗模型输出，去掉 Markdown 代码块、成对引号，并限制主题长度。
     */
    private Optional<String> sanitize(String output, boolean subject) {
        if (output == null) {
            return Optional.empty();
        }
        String cleaned = output.trim()
                .replaceFirst("^```(?:text|markdown)?\\s*", "")
                .replaceFirst("\\s*```$", "")
                .trim();
        if ((cleaned.startsWith("\"") && cleaned.endsWith("\""))
                || (cleaned.startsWith("“") && cleaned.endsWith("”"))) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        if (subject) {
            cleaned = cleaned.lines().findFirst().orElse("").trim();
            if (cleaned.length() > MAX_SUBJECT_LENGTH) {
                cleaned = cleaned.substring(0, MAX_SUBJECT_LENGTH);
            }
        }
        return cleaned.isBlank() ? Optional.empty() : Optional.of(cleaned);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
