package com.zc.iflyzcragback.service.rag.skill;

import com.zc.iflyzcragback.common.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class EmailSkill implements Skill {
    public static final String NAME = "EmailSkill";
    static final String ASK_RECIPIENT = "ASK_RECIPIENT";
    static final String ASK_SUBJECT = "ASK_SUBJECT";
    static final String ASK_CONTENT = "ASK_CONTENT";
    static final String CONFIRM = "CONFIRM";

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private final EmailDeliveryService emailDeliveryService;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String displayName() {
        return "邮件发送";
    }

    @Override
    public String description() {
        return "通过多轮对话收集收件人、主题、内容，确认后发送邮件";
    }

    @Override
    public boolean canHandle(String input) {
        return input != null && (input.contains("发邮件") || input.contains("发送邮件") || input.contains("写邮件"));
    }

    @Override
    public SkillResult start(SkillContext context) {
        return SkillResult.ask("好的，我来帮你发送邮件。请告诉我收件人的邮箱地址。",
                ASK_RECIPIENT, context.mutableState());
    }

    @Override
    public SkillResult handle(String input, SkillContext context) {
        return switch (context.getCurrentStep()) {
            case ASK_RECIPIENT -> handleRecipient(input, context);
            case ASK_SUBJECT -> handleSubject(input, context);
            case ASK_CONTENT -> handleContent(input, context);
            case CONFIRM -> handleConfirm(input, context);
            default -> SkillResult.done("邮件技能状态异常，流程已结束。", context.mutableState());
        };
    }

    private SkillResult handleRecipient(String input, SkillContext context) {
        String recipient = normalize(input);
        if (!EMAIL_PATTERN.matcher(recipient).matches()) {
            return SkillResult.ask("邮箱格式不正确，请重新输入收件人的邮箱地址。",
                    ASK_RECIPIENT, context.mutableState());
        }
        Map<String, Object> state = context.mutableState();
        state.put("recipient", recipient);
        return SkillResult.ask("收件人已设置为：" + recipient + "。请输入邮件主题。",
                ASK_SUBJECT, state);
    }

    private SkillResult handleSubject(String input, SkillContext context) {
        String subject = normalize(input);
        if (subject.isBlank()) {
            return SkillResult.ask("邮件主题不能为空，请输入邮件主题。", ASK_SUBJECT, context.mutableState());
        }
        Map<String, Object> state = context.mutableState();
        state.put("subject", subject);
        return SkillResult.ask("邮件主题已设置为“" + subject + "”。请输入邮件内容。",
                ASK_CONTENT, state);
    }

    private SkillResult handleContent(String input, SkillContext context) {
        String content = normalize(input);
        if (content.isBlank()) {
            return SkillResult.ask("邮件内容不能为空，请输入邮件内容。", ASK_CONTENT, context.mutableState());
        }
        Map<String, Object> state = context.mutableState();
        state.put("content", content);
        return SkillResult.ask("""
                邮件内容已设置。请确认发送：
                收件人：%s
                主题：%s
                内容：%s

                回复“确认”发送，回复“取消”放弃。
                """.formatted(state.get("recipient"), state.get("subject"), content), CONFIRM, state);
    }

    private SkillResult handleConfirm(String input, SkillContext context) {
        String confirm = normalize(input);
        if (!"确认".equals(confirm) && !"发送".equals(confirm)) {
            return SkillResult.ask("请回复“确认”发送邮件，或回复“取消”放弃。", CONFIRM, context.mutableState());
        }
        Map<String, Object> state = context.mutableState();
        try {
            emailDeliveryService.send(
                    String.valueOf(state.get("recipient")),
                    String.valueOf(state.get("subject")),
                    String.valueOf(state.get("content")));
            return SkillResult.done("邮件已发送成功！", state);
        } catch (BizException e) {
            return SkillResult.done("邮件发送失败：" + e.getMessage(), state);
        }
    }

    private String normalize(String input) {
        return input == null ? "" : input.trim();
    }
}
