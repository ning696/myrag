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
public class SkillResult {
    private String response;
    private String nextStep;
    private boolean completed;
    @Builder.Default
    private Map<String, Object> updatedState = new LinkedHashMap<>();
    private String failureReason;

    public static SkillResult ask(String response, String nextStep, Map<String, Object> state) {
        return SkillResult.builder()
                .response(response)
                .nextStep(nextStep)
                .updatedState(state)
                .completed(false)
                .build();
    }

    public static SkillResult done(String response, Map<String, Object> state) {
        return SkillResult.builder()
                .response(response)
                .updatedState(state)
                .completed(true)
                .build();
    }
}
