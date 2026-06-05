package com.zc.iflyzcragback.service.rag.skill;

public interface Skill {
    String name();

    String displayName();

    String description();

    boolean canHandle(String input);

    SkillResult start(SkillContext context);

    default SkillResult start(String input, SkillContext context) {
        return start(context);
    }

    SkillResult handle(String input, SkillContext context);

    default boolean available() {
        return true;
    }
}
