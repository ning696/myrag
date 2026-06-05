package com.zc.iflyzcragback.service.rag.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkillRouter {
    private static final double MIN_CONFIDENCE = 0.6;
    private static final String ROUTER_PROMPT = """
            你是 SkillRouter，只判断用户是否要启动一个任务型多轮 Skill，不要回答用户问题。

            可用 Skill：
            %s

            判断规则：
            - 只有需要进入“多轮任务流程”的请求才选择 Skill；否则选择 NONE。
            - WeatherSkill 负责天气查询流程。用户询问天气、天气预报、是否下雨、冷热、风力等，即使缺少城市或日期，也应优先选择 WeatherSkill，让 Skill 继续追问缺失信息。
            - EmailSkill 负责真实发邮件流程。用户表达要发邮件、写邮件并发送、给某人发送通知等，应选择 EmailSkill，让 Skill 收集收件人、主题、内容并确认。
            - 如果用户明确要求根据知识库、文档、上传资料、代码、设计、实现、API 原理来回答，不要选择 Skill，应选择 NONE。
            - 普通闲聊、知识解释、公开新闻/价格/汇率/政策查询不要选择 Skill。
            - 不能因为句子里出现“天气”“邮件”就一定触发；要判断用户是否要执行对应任务。
            - 只输出 JSON，不要 Markdown。

            应选择 WeatherSkill 的例子：
            - 今天天气怎么样
            - 明天天气如何
            - 北京今天下雨吗
            - 查一下上海后天天气
            - 天气预报

            应选择 EmailSkill 的例子：
            - 我要发邮件
            - 帮我给张三发送会议通知
            - 写封邮件并发送

            应选择 NONE 的例子：
            - 天气查询 Skill 怎么实现
            - 根据文档说明天气 API 调用流程
            - 邮件发送模块的代码怎么设计
            - 今天黄金价格是多少
            - 根据知识库回答这个制度问题

            用户输入：%s

            输出格式：
            {"skillName":"EmailSkill|WeatherSkill|NONE","confidence":0到1,"reason":"简短原因"}
            """;

    private final SkillService skillService;
    private final ObjectProvider<ChatLanguageModel> chatModelProvider;
    private final ObjectMapper objectMapper;

    public Optional<Decision> route(String input) {
        List<Skill> enabledSkills = skillService.enabledSkills();
        ChatLanguageModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null || enabledSkills.isEmpty()) {
            return Optional.empty();
        }
        try {
            String output = chatModel.chat(ROUTER_PROMPT.formatted(formatSkills(enabledSkills), input));
            JsonNode root = objectMapper.readTree(stripMarkdownFence(output));
            String skillName = root.path("skillName").asText("NONE");
            double confidence = root.path("confidence").asDouble(0.0);
            String reason = root.path("reason").asText("LLM 识别");
            if (confidence < MIN_CONFIDENCE || "NONE".equalsIgnoreCase(skillName)) {
                return Optional.empty();
            }
            return enabledSkills.stream()
                    .filter(skill -> skill.name().equals(skillName))
                    .findFirst()
                    .map(skill -> new Decision(skill, reason));
        } catch (Exception e) {
            log.warn("SkillRouter failed, continue normal RAG flow. input=\"{}\"", input, e);
            return Optional.empty();
        }
    }

    private String formatSkills(List<Skill> skills) {
        StringBuilder sb = new StringBuilder();
        for (Skill skill : skills) {
            sb.append("- ").append(skill.name())
                    .append(": ").append(skill.description())
                    .append("\n");
        }
        return sb.toString();
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

    public record Decision(Skill skill, String reason) {
    }
}
