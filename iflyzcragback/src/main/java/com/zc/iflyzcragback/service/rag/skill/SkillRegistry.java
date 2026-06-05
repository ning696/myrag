package com.zc.iflyzcragback.service.rag.skill;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class SkillRegistry {
    private final Map<String, Skill> skills;

    public SkillRegistry(List<Skill> registeredSkills) {
        Map<String, Skill> ordered = new LinkedHashMap<>();
        registeredSkills.stream()
                .sorted(Comparator.comparing(Skill::name))
                .forEach(skill -> ordered.put(skill.name(), skill));
        this.skills = Map.copyOf(ordered);
    }

    public List<Skill> list() {
        return skills.values().stream()
                .sorted(Comparator.comparing(Skill::name))
                .toList();
    }

    public Optional<Skill> find(String name) {
        return Optional.ofNullable(skills.get(name));
    }
}
