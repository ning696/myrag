package com.zc.iflyzcragback.service.rag.skill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkillOrchestrator {
    private final SkillStateManager stateManager;
    private final SkillRegistry registry;
    private final SkillRouter router;

    public Optional<SkillTurnResult> handle(String sessionId, String input, Long userId) {
        Optional<SkillContext> active = stateManager.activeContext(userId, sessionId);
        if (active.isPresent()) {
            SkillContext context = active.get();
            if (isCancel(input)) {
                stateManager.clear(userId, sessionId);
                return Optional.of(new SkillTurnResult("已取消当前技能流程，接下来可以继续普通问答。",
                        context.getSkillName(), context.getCurrentStep(), true, "用户取消"));
            }
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

        return router.route(input)
                .map(decision -> startSkill(decision.skill(), sessionId, userId, decision.reason()));
    }

    private SkillTurnResult startSkill(Skill skill, String sessionId, Long userId, String reason) {
        SkillContext context = SkillContext.builder()
                .userId(userId)
                .sessionId(sessionId)
                .skillName(skill.name())
                .currentStep("INIT")
                .stateData(new LinkedHashMap<>())
                .build();
        SkillResult result = skill.start(context);
        persistOrClear(context, result);
        log.info("Skill started | userId={} | sessionId={} | skill={} | nextStep={} | reason={}",
                userId, sessionId, skill.name(), result.getNextStep(), reason);
        return new SkillTurnResult(result.getResponse(), skill.name(), result.getNextStep(),
                result.isCompleted(), reason);
    }

    private SkillTurnResult continueSkill(Skill skill, String input, SkillContext context) {
        SkillResult result = skill.handle(input, context);
        persistOrClear(context, result);
        log.info("Skill progressed | userId={} | sessionId={} | skill={} | nextStep={} | completed={}",
                context.getUserId(), context.getSessionId(), skill.name(), result.getNextStep(), result.isCompleted());
        return new SkillTurnResult(result.getResponse(), skill.name(), result.getNextStep(),
                result.isCompleted(), "继续技能流程");
    }

    private void persistOrClear(SkillContext context, SkillResult result) {
        if (result.isCompleted()) {
            stateManager.clear(context.getUserId(), context.getSessionId());
        } else {
            stateManager.save(context, result);
        }
    }

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
