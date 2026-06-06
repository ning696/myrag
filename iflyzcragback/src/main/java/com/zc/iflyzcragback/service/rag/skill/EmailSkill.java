package com.zc.iflyzcragback.service.rag.skill;

import com.zc.iflyzcragback.common.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
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
    private static final Pattern EMAIL_FIND_PATTERN = Pattern.compile("[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+");
    private static final Pattern SUBJECT_PATTERN = Pattern.compile("(?:邮件)?(?:主题|标题)\\s*(?:为|是|:|：|改为|改成)?\\s*([^，,。；;\\n]+)");
    private static final Pattern CONTENT_PATTERN = Pattern.compile("(?:邮件)?(?:内容|正文)\\s*(?:为|是|:|：|改为|改成)?\\s*(.+?)(?=(?:，|,|。|；|;)?\\s*(?:他|她|对方|收件人)?的?邮箱\\s*(?:为|是|:|：)|$)", Pattern.DOTALL);
    private static final Pattern RECIPIENT_PATTERN = Pattern.compile("(?:收件人|对方|他|她)?的?(?:邮箱|邮件地址|收件地址)\\s*(?:为|是|:|：|改为|改成)?\\s*([A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+)");
    private static final String RECIPIENT = "recipient";
    private static final String SUBJECT = "subject";
    private static final String CONTENT = "content";
    private static final String DRAFT_BRIEF = "draftBrief";
    private static final String SUBJECT_DRAFT_REQUESTED = "subjectDraftRequested";
    private static final String CONTENT_DRAFT_REQUESTED = "contentDraftRequested";

    private final EmailDeliveryService emailDeliveryService;
    private final EmailDraftService emailDraftService;

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
    public SkillResult start(String input, SkillContext context) {
        Map<String, Object> state = context.mutableState();
        mergeExtractedFields(input, state);
        if (isRecipientDraftRequest(input)) {
            return rejectRecipientDraft(state);
        }
        SkillResult draftResult = applyRequestedDrafts(input, state);
        if (draftResult != null) {
            return draftResult;
        }
        return askForMissingOrConfirm(state);
    }

    @Override
    public SkillResult handle(String input, SkillContext context) {
        if (isRecipientDraftRequest(input)) {
            return rejectRecipientDraft(context.mutableState());
        }
        Map<String, Object> state = context.mutableState();
        FieldUpdates updates = extractUpdates(input);
        if (updates.hasAny()) {
            applyUpdates(updates, state);
            return askForMissingOrConfirm(state);
        }
        return switch (context.getCurrentStep()) {
            case ASK_RECIPIENT -> handleRecipient(input, context);
            case ASK_SUBJECT -> handleSubject(input, context);
            case ASK_CONTENT -> handleContent(input, context);
            case CONFIRM -> handleConfirm(input, context);
            default -> SkillResult.done("邮件技能状态异常，流程已结束。", context.mutableState());
        };
    }

    private SkillResult handleRecipient(String input, SkillContext context) {
        String recipient = extractRecipient(input).orElse(normalize(input));
        if (!EMAIL_PATTERN.matcher(recipient).matches()) {
            return SkillResult.ask("邮箱格式不正确，请重新输入收件人的邮箱地址。",
                    ASK_RECIPIENT, context.mutableState());
        }
        Map<String, Object> state = context.mutableState();
        state.put(RECIPIENT, recipient);
        return askForMissingOrConfirm(state);
    }

    private SkillResult handleSubject(String input, SkillContext context) {
        if (isSubjectDraftRequest(input)) {
            return draftSubject(input, context.mutableState());
        }
        String subject = normalize(input);
        if (subject.isBlank()) {
            return SkillResult.ask("邮件主题不能为空，请输入邮件主题。", ASK_SUBJECT, context.mutableState());
        }
        Map<String, Object> state = context.mutableState();
        state.put(SUBJECT, subject);
        return askForMissingOrConfirm(state);
    }

    private SkillResult handleContent(String input, SkillContext context) {
        if (isContentDraftRequest(input)) {
            return draftContent(input, context.mutableState());
        }
        String content = normalize(input);
        if (content.isBlank()) {
            return SkillResult.ask("邮件内容不能为空，请输入邮件内容。", ASK_CONTENT, context.mutableState());
        }
        Map<String, Object> state = context.mutableState();
        state.put(CONTENT, content);
        return askForMissingOrConfirm(state);
    }

    private SkillResult handleConfirm(String input, SkillContext context) {
        String confirm = normalize(input);
        Map<String, Object> state = context.mutableState();
        if (isSubjectDraftRequest(input)) {
            return draftSubject(input, state);
        }
        if (isContentDraftRequest(input)) {
            return draftContent(input, state);
        }
        if (!isConfirm(confirm)) {
            return SkillResult.ask("请回复“确认”发送邮件，或说明要修改的收件人、主题或内容。", CONFIRM, state);
        }
        try {
            emailDeliveryService.send(
                    String.valueOf(state.get(RECIPIENT)),
                    String.valueOf(state.get(SUBJECT)),
                    String.valueOf(state.get(CONTENT)));
            return SkillResult.done("邮件已发送成功！", state);
        } catch (BizException e) {
            return SkillResult.done("邮件发送失败：" + e.getMessage(), state);
        }
    }

    private SkillResult applyRequestedDrafts(String input, Map<String, Object> state) {
        if (isTrue(state.get(SUBJECT_DRAFT_REQUESTED)) && isBlank(state.get(SUBJECT))) {
            SkillResult result = draftSubject(input, state);
            if (ASK_SUBJECT.equals(result.getNextStep())) {
                return result;
            }
        }
        if (isTrue(state.get(CONTENT_DRAFT_REQUESTED)) && isBlank(state.get(CONTENT))) {
            SkillResult result = draftContent(input, state);
            if (ASK_CONTENT.equals(result.getNextStep())) {
                return result;
            }
        }
        return null;
    }

    private SkillResult draftSubject(String input, Map<String, Object> state) {
        String brief = draftBrief(input, state);
        Optional<String> subject = emailDraftService.draftSubject(brief, state);
        if (subject.isEmpty()) {
            return SkillResult.ask("可以，我来帮你写主题。请先告诉我这封邮件的目的或关键要点。", ASK_SUBJECT, state);
        }
        state.put(SUBJECT, subject.get());
        return askForMissingOrConfirm(state);
    }

    private SkillResult draftContent(String input, Map<String, Object> state) {
        String brief = draftBrief(input, state);
        Optional<String> content = emailDraftService.draftContent(brief, state);
        if (content.isEmpty()) {
            return SkillResult.ask("可以，我来帮你写内容。请先告诉我这封邮件要表达的要点。", ASK_CONTENT, state);
        }
        state.put(CONTENT, content.get());
        return askForMissingOrConfirm(state);
    }

    private SkillResult askForMissingOrConfirm(Map<String, Object> state) {
        String recipient = stringValue(state.get(RECIPIENT));
        if (recipient.isBlank()) {
            return SkillResult.ask("好的，我来帮你发送邮件。请告诉我收件人的邮箱地址。", ASK_RECIPIENT, state);
        }
        if (!EMAIL_PATTERN.matcher(recipient).matches()) {
            state.remove(RECIPIENT);
            return SkillResult.ask("邮箱格式不正确，请重新输入收件人的邮箱地址。", ASK_RECIPIENT, state);
        }
        if (isBlank(state.get(SUBJECT))) {
            return SkillResult.ask("收件人已设置为：" + recipient + "。请输入邮件主题。", ASK_SUBJECT, state);
        }
        if (isBlank(state.get(CONTENT))) {
            return SkillResult.ask("邮件主题已设置为“" + state.get(SUBJECT) + "”。请输入邮件内容。", ASK_CONTENT, state);
        }
        return buildConfirmation(state);
    }

    private SkillResult buildConfirmation(Map<String, Object> state) {
        return SkillResult.ask("""
                请确认邮件信息：
                收件人：%s
                主题：%s
                内容：%s

                回复“确认”或“确认发送”发送；如需修改，请直接说“修改主题为...”“内容改成...”或“收件人改为...”。回复“取消”放弃。
                """.formatted(state.get(RECIPIENT), state.get(SUBJECT), state.get(CONTENT)), CONFIRM, state);
    }

    private SkillResult rejectRecipientDraft(Map<String, Object> state) {
        return SkillResult.ask("收件人邮箱需要你提供准确地址，我不能代写或猜测。请告诉我收件人的邮箱地址。",
                ASK_RECIPIENT, state);
    }

    private void mergeExtractedFields(String input, Map<String, Object> state) {
        FieldUpdates updates = extractUpdates(input);
        applyUpdates(updates, state);
        extractRecipient(input).ifPresent(recipient -> state.putIfAbsent(RECIPIENT, recipient));
        rememberDraftBrief(input, state);
    }

    private FieldUpdates extractUpdates(String input) {
        String normalized = normalize(input);
        Map<String, String> values = new LinkedHashMap<>();
        extractRecipient(normalized).ifPresent(value -> values.put(RECIPIENT, value));
        extractFirst(SUBJECT_PATTERN, normalized).ifPresent(value -> values.put(SUBJECT, value));
        extractFirst(CONTENT_PATTERN, normalized).ifPresent(value -> values.put(CONTENT, value));
        return new FieldUpdates(values);
    }

    private void applyUpdates(FieldUpdates updates, Map<String, Object> state) {
        updates.values().forEach((key, value) -> {
            if (!value.isBlank()) {
                state.put(key, value);
            }
        });
    }

    private Optional<String> extractRecipient(String input) {
        String normalized = normalize(input);
        Optional<String> marked = extractFirst(RECIPIENT_PATTERN, normalized);
        if (marked.isPresent()) {
            return marked;
        }
        Matcher matcher = EMAIL_FIND_PATTERN.matcher(normalized);
        return matcher.find() ? Optional.of(matcher.group()) : Optional.empty();
    }

    private Optional<String> extractFirst(Pattern pattern, String input) {
        Matcher matcher = pattern.matcher(input);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(cleanExtractedValue(matcher.group(1)));
    }

    private String cleanExtractedValue(String value) {
        return normalize(value).replaceAll("^[：:，,。；;\\s]+|[：:，,。；;\\s]+$", "");
    }

    private void rememberDraftBrief(String input, Map<String, Object> state) {
        String brief = normalize(input)
                .replaceAll("[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+", "")
                .replaceAll("(帮我|请|麻烦)?(写|设计|生成|拟)(一个|一下|封|份)?(邮件)?(主题|标题|内容|正文)", "")
                .replaceAll("(发|发送|写)(一封|一份)?邮件", "")
                .replaceAll("(收件人|对方|他|她)?的?(邮箱|邮件地址|收件地址)\\s*(为|是|:|：)?", "")
                .trim();
        if (!brief.isBlank()) {
            state.put(DRAFT_BRIEF, brief);
        }
    }

    private String draftBrief(String input, Map<String, Object> state) {
        rememberDraftBrief(input, state);
        return stringValue(state.get(DRAFT_BRIEF));
    }

    private boolean isRecipientDraftRequest(String input) {
        String normalized = normalize(input);
        if (extractRecipient(normalized).isPresent()) {
            return false;
        }
        return (normalized.contains("帮") || normalized.contains("你来") || normalized.contains("编") || normalized.contains("随便"))
                && (normalized.contains("邮箱") || normalized.contains("邮件地址") || normalized.contains("收件人"));
    }

    private boolean isSubjectDraftRequest(String input) {
        String normalized = normalize(input);
        return (normalized.contains("帮") || normalized.contains("设计") || normalized.contains("生成") || normalized.contains("拟") || normalized.contains("写"))
                && (normalized.contains("主题") || normalized.contains("标题"));
    }

    private boolean isContentDraftRequest(String input) {
        String normalized = normalize(input);
        return (normalized.contains("帮") || normalized.contains("生成") || normalized.contains("拟") || normalized.contains("写"))
                && (normalized.contains("内容") || normalized.contains("正文"));
    }

    private boolean isConfirm(String input) {
        return "确认".equals(input)
                || "确认发送".equals(input)
                || "发送".equals(input)
                || "发送吧".equals(input);
    }

    private boolean isTrue(Object value) {
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private boolean isBlank(Object value) {
        return value == null || String.valueOf(value).trim().isBlank();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String normalize(String input) {
        return input == null ? "" : input.trim();
    }

    private record FieldUpdates(Map<String, String> values) {
        private boolean hasAny() {
            return !values.isEmpty();
        }
    }
}
