package com.zc.iflyzcragback.service.rag.skill;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.iflyzcragback.entity.SkillStateEntity;
import com.zc.iflyzcragback.mapper.SkillStateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkillStateManager {
    private static final int ACTIVE = 0;
    private static final int EXPIRE_MINUTES = 30;

    private final SkillStateMapper mapper;
    private final ObjectMapper objectMapper;

    public Optional<SkillContext> activeContext(Long userId, String sessionId) {
        try {
            SkillStateEntity state = selectActive(userId, sessionId);
            if (state == null) {
                return Optional.empty();
            }
            if (state.getExpiresAt() != null && state.getExpiresAt().isBefore(LocalDateTime.now())) {
                clear(userId, sessionId);
                log.info("Skill state expired and cleared | userId={} | sessionId={}", userId, sessionId);
                return Optional.empty();
            }
            return Optional.of(toContext(state));
        } catch (DataAccessException e) {
            log.warn("Skill state table unavailable, skip skill flow. userId={} sessionId={}", userId, sessionId, e);
            return Optional.empty();
        }
    }

    public void save(SkillContext context, SkillResult result) {
        try {
            SkillStateEntity existing = selectActive(context.getUserId(), context.getSessionId());
            SkillStateEntity state = existing == null ? new SkillStateEntity() : existing;
            state.setUserId(context.getUserId());
            state.setSessionId(context.getSessionId());
            state.setSkillName(context.getSkillName());
            state.setCurrentStep(result.getNextStep());
            state.setStateData(objectMapper.writeValueAsString(result.getUpdatedState()));
            state.setIsCompleted(result.isCompleted() ? 1 : 0);
            state.setExpiresAt(LocalDateTime.now().plusMinutes(EXPIRE_MINUTES));
            if (state.getId() == null) {
                mapper.insert(state);
            } else {
                mapper.updateById(state);
            }
        } catch (Exception e) {
            log.warn("Failed to save skill state | userId={} | sessionId={}",
                    context.getUserId(), context.getSessionId(), e);
        }
    }

    public void clear(Long userId, String sessionId) {
        try {
            mapper.delete(new LambdaQueryWrapper<SkillStateEntity>()
                    .eq(SkillStateEntity::getUserId, userId)
                    .eq(SkillStateEntity::getSessionId, sessionId)
                    .eq(SkillStateEntity::getIsCompleted, ACTIVE));
        } catch (DataAccessException e) {
            log.warn("Failed to clear skill state | userId={} | sessionId={}", userId, sessionId, e);
        }
    }

    private SkillStateEntity selectActive(Long userId, String sessionId) {
        return mapper.selectOne(new LambdaQueryWrapper<SkillStateEntity>()
                .eq(SkillStateEntity::getUserId, userId)
                .eq(SkillStateEntity::getSessionId, sessionId)
                .eq(SkillStateEntity::getIsCompleted, ACTIVE)
                .last("LIMIT 1"));
    }

    private SkillContext toContext(SkillStateEntity state) {
        return SkillContext.builder()
                .userId(state.getUserId())
                .sessionId(state.getSessionId())
                .skillName(state.getSkillName())
                .currentStep(state.getCurrentStep())
                .stateData(parseState(state.getStateData()))
                .build();
    }

    private Map<String, Object> parseState(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Invalid skill state JSON ignored");
            return new LinkedHashMap<>();
        }
    }
}
