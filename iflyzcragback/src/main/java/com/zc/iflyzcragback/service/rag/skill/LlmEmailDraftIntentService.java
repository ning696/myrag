package com.zc.iflyzcragback.service.rag.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 邮件代写意图识别服务。
 *
 * <p>它不生成邮件正文，只判断用户当前这句话是在直接填写字段，还是希望 AI 帮忙生成主题、
 * 正文或二者。识别结果低于置信度阈值时返回空，避免误覆盖用户已经输入的内容。</p>
 */
public class LlmEmailDraftIntentService implements EmailDraftIntentService {
    private static final double MIN_CONFIDENCE = 0.65;

    private final ObjectProvider<ChatLanguageModel> chatModelProvider;
    private final ObjectMapper objectMapper;

    @Override
    /**
     * 根据当前步骤、已知邮件状态和用户输入判断代写目标。
     */
    public Optional<EmailDraftIntent> detect(String input, String currentStep, Map<String, Object> state) {
        ChatLanguageModel model = chatModelProvider.getIfAvailable();
        String normalized = input == null ? "" : input.trim();
        if (model == null || normalized.isBlank()) {
            return Optional.empty();
        }
        try {
            String output = model.chat("""
                    你是邮件技能的意图判断器，只判断用户当前这句话是否是在请求 AI 代写邮件主题或正文。
                    不要生成邮件内容，只输出 JSON。

                    当前流程步骤：%s
                    已知邮件信息：
                    收件人：%s
                    已有主题：%s
                    已有正文：%s

                    判断规则：
                    1. 如果当前步骤是 ASK_SUBJECT，用户给的是邮件目的、要求、希望对方做的事、提醒要点，而不是可直接使用的短标题，通常选择 BOTH，同时生成主题和正文。
                    2. 如果当前步骤是 ASK_SUBJECT，用户给的是可直接作为标题的短语，例如“会议通知”“挑战杯参会提醒”，选择 NONE。
                    3. 如果当前步骤是 ASK_CONTENT，用户给的是要表达的目的或关键点，而不是可直接发送的完整正文，选择 CONTENT。
                    4. 如果当前步骤是 ASK_CONTENT，用户给的是完整可发送正文，选择 NONE。
                    5. 如果当前步骤是 CONFIRM，只有用户明确想重写、润色或生成主题/正文时，才选择 SUBJECT、CONTENT 或 BOTH；否则选择 NONE。
                    6. 如果不确定，选择 NONE，避免误改用户已经输入的字段。

                    用户输入：%s

                    输出格式：
                    {"target":"SUBJECT|CONTENT|BOTH|NONE","brief":"代写要点；没有则空字符串","confidence":0到1}
                    """.formatted(
                    currentStep,
                    stringValue(state.get("recipient")),
                    stringValue(state.get("subject")),
                    stringValue(state.get("content")),
                    normalized));
            // 模型被要求只输出 JSON；这里仍兼容它偶尔包一层 Markdown 代码块的情况。
            JsonNode root = objectMapper.readTree(stripMarkdownFence(output));
            EmailDraftTarget target = parseTarget(root.path("target").asText("NONE"));
            double confidence = root.path("confidence").asDouble(0.0);
            // 低置信度或 NONE 都按“用户直接输入”处理，减少误改主题/正文的概率。
            if (target == EmailDraftTarget.NONE || confidence < MIN_CONFIDENCE) {
                return Optional.empty();
            }
            String brief = root.path("brief").asText(normalized).trim();
            if (brief.isBlank()) {
                brief = normalized;
            }
            return Optional.of(new EmailDraftIntent(target, brief, confidence));
        } catch (Exception e) {
            log.warn("Email draft intent detection failed, continue as direct input. step={} input=\"{}\"",
                    currentStep, normalized, e);
            return Optional.empty();
        }
    }

    /**
     * 将模型返回的字符串安全转换为枚举；非法值一律降级为 NONE。
     */
    private EmailDraftTarget parseTarget(String value) {
        try {
            return EmailDraftTarget.valueOf(value == null ? "NONE" : value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return EmailDraftTarget.NONE;
        }
    }

    /**
     * 去掉模型可能附带的 Markdown fence，便于 JSON 解析。
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

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
