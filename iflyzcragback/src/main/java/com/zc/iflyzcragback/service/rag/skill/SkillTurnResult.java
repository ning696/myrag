package com.zc.iflyzcragback.service.rag.skill;

public record SkillTurnResult(String answer,
                              String skillName,
                              String skillStep,
                              boolean completed,
                              String reason) {
}
