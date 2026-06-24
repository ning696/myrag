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
/**
 * 邮件发送技能。
 *
 * <p>这是一个有真实外部副作用的多轮技能：先收集收件人、主题和正文，再向用户展示确认信息，
 * 只有用户明确回复确认后才调用 {@link EmailDeliveryService} 发送邮件。</p>
 *
 * <p>LLM 在这个技能中只承担辅助角色：识别“是否需要代写主题/正文”和生成草稿。收件人邮箱必须
 * 由用户明确提供，不能由模型猜测或补全。</p>
 */
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
    private final EmailDraftIntentService emailDraftIntentService;

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

    /**
     * 无槽位启动时，从最关键且不能猜测的收件人邮箱开始追问。
     */
    @Override
    public SkillResult start(SkillContext context) {
        return SkillResult.ask("好的，我来帮你发送邮件。请告诉我收件人的邮箱地址。",
                ASK_RECIPIENT, context.mutableState());
    }

    /**
     * 带原始用户输入启动时，先尝试抽取邮箱、主题、正文和代写意图。
     */
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

    /**
     * 继续处理邮件技能的下一轮输入。
     *
     * <p>处理顺序很重要：先拦截“帮我写收件人邮箱”这类不安全请求，再识别明确的代写意图，
     * 最后才做字段更新和按当前步骤处理。这样可以避免“根据主题生成内容”被误解析成修改主题。</p>
     */
    @Override
    public SkillResult handle(String input, SkillContext context) {
        if (isRecipientDraftRequest(input)) {
            return rejectRecipientDraft(context.mutableState());
        }
        Map<String, Object> state = context.mutableState();
        // 显式代写短语优先于正则字段抽取，避免“主题/内容”关键词误触发字段覆盖。
        Optional<EmailDraftIntent> explicitDraftIntent = detectExplicitDraftIntent(input, state);
        if (explicitDraftIntent.map(EmailDraftIntent::requestsSubjectDraft).orElse(false)) {
            return draftSubjectAndMaybeContent(explicitDraftIntent.get(), state);
        }
        if (explicitDraftIntent.map(EmailDraftIntent::requestsContentDraft).orElse(false)) {
            return draftContent(explicitDraftIntent.get().brief(), state);
        }
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

    /**
     * 收集并校验收件人邮箱。
     */
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

    /**
     * 收集主题；如果用户给的是写作要点，则转入主题/正文草稿生成。
     */
    private SkillResult handleSubject(String input, SkillContext context) {
        String subject = normalize(input);
        if (subject.isBlank()) {
            return SkillResult.ask("邮件主题不能为空，请输入邮件主题。", ASK_SUBJECT, context.mutableState());
        }
        Map<String, Object> state = context.mutableState();
        Optional<EmailDraftIntent> intent = emailDraftIntentService.detect(input, ASK_SUBJECT, state);
        if (intent.map(EmailDraftIntent::requestsSubjectDraft).orElse(false)) {
            return draftSubjectAndMaybeContent(intent.get(), state);
        }
        state.put(SUBJECT, subject);
        return askForMissingOrConfirm(state);
    }

    /**
     * 收集正文；如果用户要求“帮我写正文”，则调用草稿生成服务。
     */
    private SkillResult handleContent(String input, SkillContext context) {
        String content = normalize(input);
        if (content.isBlank()) {
            return SkillResult.ask("邮件内容不能为空，请输入邮件内容。", ASK_CONTENT, context.mutableState());
        }
        Map<String, Object> state = context.mutableState();
        Optional<EmailDraftIntent> intent = emailDraftIntentService.detect(input, ASK_CONTENT, state);
        if (intent.map(EmailDraftIntent::requestsContentDraft).orElse(false)) {
            return draftContent(intent.get().brief(), state);
        }
        state.put(CONTENT, content);
        return askForMissingOrConfirm(state);
    }

    /**
     * 确认步骤：允许继续修改/代写；只有明确确认时才真正发送邮件。
     */
    private SkillResult handleConfirm(String input, SkillContext context) {
        String confirm = normalize(input);
        Map<String, Object> state = context.mutableState();
        Optional<EmailDraftIntent> intent = emailDraftIntentService.detect(input, CONFIRM, state);
        if (intent.map(EmailDraftIntent::requestsSubjectDraft).orElse(false)) {
            return draftSubjectAndMaybeContent(intent.get(), state);
        }
        if (intent.map(EmailDraftIntent::requestsContentDraft).orElse(false)) {
            return draftContent(intent.get().brief(), state);
        }
        if (!isConfirm(confirm)) {
            return SkillResult.ask("请回复“确认”发送邮件，或说明要修改的收件人、主题或内容。", CONFIRM, state);
        }
        try {
            // 真实外部副作用只允许出现在确认步骤之后。
            emailDeliveryService.send(
                    String.valueOf(state.get(RECIPIENT)),
                    String.valueOf(state.get(SUBJECT)),
                    String.valueOf(state.get(CONTENT)));
            return SkillResult.done("邮件已发送成功！", state);
        } catch (BizException e) {
            return SkillResult.done("邮件发送失败：" + e.getMessage(), state);
        }
    }

    /**
     * 处理 SkillRouter 在启动阶段识别出的代写请求。
     */
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

    /**
     * 先生成主题；如果意图要求 BOTH 且正文仍缺失，再继续生成正文。
     */
    private SkillResult draftSubjectAndMaybeContent(EmailDraftIntent intent, Map<String, Object> state) {
        SkillResult result = draftSubject(intent.brief(), state);
        if (intent.requestsContentDraft()
                && !ASK_SUBJECT.equals(result.getNextStep())
                && isBlank(state.get(CONTENT))) {
            return draftContent(intent.brief(), state);
        }
        return result;
    }

    /**
     * 生成主题草稿，失败时继续追问写作要点。
     */
    private SkillResult draftSubject(String input, Map<String, Object> state) {
        String brief = draftBrief(input, state);
        Optional<String> subject = emailDraftService.draftSubject(brief, state);
        if (subject.isEmpty()) {
            return SkillResult.ask("可以，我来帮你写主题。请先告诉我这封邮件的目的或关键要点。", ASK_SUBJECT, state);
        }
        state.put(SUBJECT, subject.get());
        return askForMissingOrConfirm(state);
    }

    /**
     * 生成正文草稿，失败时继续追问写作要点。
     */
    private SkillResult draftContent(String input, Map<String, Object> state) {
        String brief = draftBrief(input, state);
        Optional<String> content = emailDraftService.draftContent(brief, state);
        if (content.isEmpty()) {
            return SkillResult.ask("可以，我来帮你写内容。请先告诉我这封邮件要表达的要点。", ASK_CONTENT, state);
        }
        state.put(CONTENT, content.get());
        return askForMissingOrConfirm(state);
    }

    /**
     * 根据当前槽位完整度决定下一步：缺收件人、缺主题、缺正文，或进入确认。
     */
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

    /**
     * 构造发送前确认信息。用户仍可在该步骤修改字段或取消。
     */
    private SkillResult buildConfirmation(Map<String, Object> state) {
        return SkillResult.ask("""
                请确认邮件信息：
                收件人：%s
                主题：%s
                内容：%s

                回复“确认”或“确认发送”发送；如需修改，请直接说“修改主题为...”“内容改成...”或“收件人改为...”。回复“取消”放弃。
                """.formatted(state.get(RECIPIENT), state.get(SUBJECT), state.get(CONTENT)), CONFIRM, state);
    }

    /**
     * 用确定性关键词识别显式代写请求。
     *
     * <p>这层规则用于覆盖 LLM 之前的高确定性表达，例如“根据主题帮我生成内容”。</p>
     */
    private Optional<EmailDraftIntent> detectExplicitDraftIntent(String input, Map<String, Object> state) {
        String normalized = normalize(input);
        if (normalized.isBlank() || isExplicitFieldUpdate(normalized)) {
            return Optional.empty();
        }

        boolean requestsSubject = containsAny(normalized,
                "写主题", "写标题", "生成主题", "生成标题", "生产主题", "生产标题",
                "拟主题", "拟标题", "起草主题", "起草标题", "设计主题", "设计标题");
        boolean requestsContent = containsAny(normalized,
                "写内容", "写正文", "生成内容", "生成正文", "生产内容", "生产正文",
                "拟内容", "拟正文", "起草内容", "起草正文", "撰写内容", "撰写正文");
        boolean contentFromSubject = containsAny(normalized, "根据主题", "按主题", "依据主题", "基于主题",
                "根据标题", "按标题", "依据标题", "基于标题")
                && containsAny(normalized, "内容", "正文", "写", "生成", "生产", "拟", "起草", "撰写");

        if (contentFromSubject) {
            requestsContent = true;
        }
        if (!requestsSubject && !requestsContent) {
            return Optional.empty();
        }

        EmailDraftTarget target = requestsSubject && requestsContent
                ? EmailDraftTarget.BOTH
                : (requestsSubject ? EmailDraftTarget.SUBJECT : EmailDraftTarget.CONTENT);
        String brief = explicitDraftBrief(normalized, target, state, contentFromSubject);
        return Optional.of(new EmailDraftIntent(target, brief, 1.0));
    }

    /**
     * 判断用户是否明确在修改字段；这类输入不应被当成代写请求。
     */
    private boolean isExplicitFieldUpdate(String input) {
        return input.matches(".*(?:主题|标题)\\s*(?:为|是|:|：|改为|改成).+")
                || input.matches(".*(?:内容|正文)\\s*(?:为|是|:|：|改为|改成).+");
    }

    /**
     * 从显式代写请求中提炼草稿要点；没有要点但有主题时，可用已有主题生成正文。
     */
    private String explicitDraftBrief(String input, EmailDraftTarget target, Map<String, Object> state, boolean contentFromSubject) {
        String brief = input
                .replaceAll("(帮我|请|麻烦|根据|按|依据|基于|已有|邮件|的)", "")
                .replaceAll("(写|设计|生成|生产|拟|起草|撰写)(一个|一下|封|份)?", "")
                .replaceAll("(主题|标题|内容|正文)", "")
                .trim();
        if (!brief.isBlank()) {
            return brief;
        }
        if ((target == EmailDraftTarget.CONTENT || target == EmailDraftTarget.BOTH || contentFromSubject)
                && !isBlank(state.get(SUBJECT))) {
            return "根据已有主题生成邮件正文";
        }
        return input;
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
    /**
     * 拒绝代写或猜测收件人邮箱，避免把邮件发送给错误对象。
     */
    private SkillResult rejectRecipientDraft(Map<String, Object> state) {
        return SkillResult.ask("收件人邮箱需要你提供准确地址，我不能代写或猜测。请告诉我收件人的邮箱地址。",
                ASK_RECIPIENT, state);
    }

    /**
     * 启动技能时合并用户一句话里已经提供的字段。
     */
    private void mergeExtractedFields(String input, Map<String, Object> state) {
        FieldUpdates updates = extractUpdates(input);
        applyUpdates(updates, state);
        extractRecipient(input).ifPresent(recipient -> state.putIfAbsent(RECIPIENT, recipient));
        rememberDraftBrief(input, state);
    }

    /**
     * 从用户输入中抽取明确字段更新，例如“主题改为...”或“内容为...”。
     */
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

    /**
     * 记住写作要点，供后续主题/正文草稿生成复用。
     */
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

    /**
     * 判断用户是否要求系统代写或猜测收件人邮箱。
     */
    private boolean isRecipientDraftRequest(String input) {
        String normalized = normalize(input);
        if (extractRecipient(normalized).isPresent()) {
            return false;
        }
        return (normalized.contains("帮") || normalized.contains("你来") || normalized.contains("编") || normalized.contains("随便"))
                && (normalized.contains("邮箱") || normalized.contains("邮件地址") || normalized.contains("收件人"));
    }

    /**
     * 发送确认词白名单。
     */
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
