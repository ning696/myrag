package com.zc.iflyzcragback.service.rag.skill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillContext {
    private Long userId;
    private String sessionId;
    private String skillName;
    private String currentStep;
    @Builder.Default
    private Map<String, Object> stateData = new LinkedHashMap<>();

    public Map<String, Object> mutableState() {
        if (stateData == null) {
            stateData = new LinkedHashMap<>();
        }
        return stateData;
    }
}
