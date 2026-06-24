package com.zc.iflyzcragback.service.rag.skill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * 技能流程总编排器。
 *
 * <p>它是 RAG 主流程进入 Skill 子系统的唯一入口，负责恢复当前会话的活跃技能、
 * 启动新技能，并在每一轮结束时保存或清理技能状态。</p>
 *
 * <p>这里不实现具体技能业务，例如邮件、天气等；具体逻辑由各个 {@link Skill}
 * 实现类负责。这样新增技能时通常只需要新增实现类和配置，不必改动 RAG 主链路。</p>
 */
public class SkillOrchestrator {
    private final SkillStateManager stateManager;
    private final SkillRegistry registry;
    private final SkillRouter router;

    /**
     * 处理一次用户输入，并判断这轮输入是否应由技能流程接管。
     *
     * <p>返回 {@link Optional#empty()} 表示没有技能要处理，调用方可以继续走普通 RAG、
     * 闲聊或工具调用；返回结果则表示本轮已经由技能完成响应。</p>
     */
    public Optional<SkillTurnResult> handle(String sessionId, String input, Long userId) {
        // 活跃技能优先级最高：当前会话有未完成流程时，本轮输入继续交给该技能。
        Optional<SkillContext> active = stateManager.activeContext(userId, sessionId);
        if (active.isPresent()) {
            SkillContext context = active.get();
            // 取消词是所有技能的统一出口，避免每个技能重复实现退出逻辑。
            if (isCancel(input)) {
                stateManager.clear(userId, sessionId);
                return Optional.of(new SkillTurnResult("已取消当前技能流程，接下来可以继续普通问答。",
                        context.getSkillName(), context.getCurrentStep(), true, "用户取消"));
            }
            // 状态只保存技能名称，因此每轮继续前都从注册表获取当前可用的技能 Bean。
            return registry.find(context.getSkillName())
                    .map(skill -> continueSkill(skill, input, context))
                    .or(() -> {
                        stateManager.clear(userId, sessionId);
                        return Optional.of(new SkillTurnResult("当前技能已不可用，流程已结束。",
                                context.getSkillName(), context.getCurrentStep(), true, "技能不存在"));
                    });
        }

        if (isCancel(input)) {
            return Optional.empty();
        }

        // 没有活跃流程时才尝试启动新技能；未命中则交还给 RagOrchestrator。
        return router.route(input)
                .map(decision -> startSkill(decision.skill(), sessionId, input, userId, decision.reason(), decision.slots()));
    }

    /**
     * 根据路由器抽取的槽位初始化技能上下文，并调用技能启动入口。
     */
    private SkillTurnResult startSkill(Skill skill, String sessionId, String input, Long userId,
                                       String reason, Map<String, String> slots) {
        Map<String, Object> stateData = new LinkedHashMap<>();
        if (slots != null) {
            stateData.putAll(slots);
        }
        SkillContext context = SkillContext.builder()
                .userId(userId)
                .sessionId(sessionId)
                .skillName(skill.name())
                .currentStep("INIT")
                .stateData(stateData)
                .build();
        SkillResult result = skill.start(input, context);
        persistOrClear(context, result);
        log.info("Skill started | userId={} | sessionId={} | skill={} | nextStep={} | reason={}",
                userId, sessionId, skill.name(), result.getNextStep(), reason);
        return new SkillTurnResult(result.getResponse(), skill.name(), result.getNextStep(),
                result.isCompleted(), reason);
    }

    /**
     * 将用户输入交给当前技能处理，并转换成前端可识别的回合结果。
     */
    private SkillTurnResult continueSkill(Skill skill, String input, SkillContext context) {
        SkillResult result = skill.handle(input, context);
        persistOrClear(context, result);
        log.info("Skill progressed | userId={} | sessionId={} | skill={} | nextStep={} | completed={}",
                context.getUserId(), context.getSessionId(), skill.name(), result.getNextStep(), result.isCompleted());
        return new SkillTurnResult(result.getResponse(), skill.name(), result.getNextStep(),
                result.isCompleted(), "继续技能流程");
    }

    /**
     * 技能未完成时保存下一步；技能完成、失败结束或被取消时清理状态。
     */
    private void persistOrClear(SkillContext context, SkillResult result) {
        if (result.isCompleted()) {
            stateManager.clear(context.getUserId(), context.getSessionId());
        } else {
            stateManager.save(context, result);
        }
    }

    /**
     * 判断用户是否明确要求退出当前技能流程。
     */
    private boolean isCancel(String input) {
        if (input == null) {
            return false;
        }
        String normalized = input.trim();
        return "取消".equals(normalized)
                || "退出".equals(normalized)
                || "停止".equals(normalized)
                || "cancel".equalsIgnoreCase(normalized)
                || "exit".equalsIgnoreCase(normalized);
    }
}
