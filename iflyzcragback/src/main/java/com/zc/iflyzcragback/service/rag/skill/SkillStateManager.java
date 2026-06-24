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
/**
 * 技能状态持久化管理器。
 *
 * <p>技能流程通常跨多轮对话完成，因此需要把当前步骤和已收集槽位保存起来。
 * 本类用 {@code userId + sessionId} 作为隔离键，保证不同用户、不同会话之间不会串线。</p>
 *
 * <p>状态只保存“继续流程所需的最小数据”，不保存敏感密钥，也不保存可重新从数据库读取的
 * 大对象。状态过期后自动清理，避免用户长时间离开后继续执行旧动作。</p>
 */
public class SkillStateManager {
    private static final int ACTIVE = 0;
    private static final int EXPIRE_MINUTES = 30;

    private final SkillStateMapper mapper;
    private final ObjectMapper objectMapper;

    /**
     * 查询当前用户、当前会话下尚未完成且未过期的技能上下文。
     */
    public Optional<SkillContext> activeContext(Long userId, String sessionId) {
        try {
            SkillStateEntity state = selectActive(userId, sessionId);
            if (state == null) {
                return Optional.empty();
            }
            // 过期状态不再继续执行，避免旧的真实世界动作被意外触发。
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

    /**
     * 保存技能下一步状态。
     *
     * <p>如果本会话已经有活跃记录，则更新原记录；否则插入新记录。每次保存都会刷新过期时间。</p>
     */
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

    /**
     * 清除当前用户、当前会话的活跃技能状态。
     */
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

    /**
     * 只选择未完成状态；已完成记录由逻辑删除或历史记录机制处理。
     */
    private SkillStateEntity selectActive(Long userId, String sessionId) {
        return mapper.selectOne(new LambdaQueryWrapper<SkillStateEntity>()
                .eq(SkillStateEntity::getUserId, userId)
                .eq(SkillStateEntity::getSessionId, sessionId)
                .eq(SkillStateEntity::getIsCompleted, ACTIVE)
                .last("LIMIT 1"));
    }

    /**
     * 将数据库状态还原为技能运行时上下文。
     */
    private SkillContext toContext(SkillStateEntity state) {
        return SkillContext.builder()
                .userId(state.getUserId())
                .sessionId(state.getSessionId())
                .skillName(state.getSkillName())
                .currentStep(state.getCurrentStep())
                .stateData(parseState(state.getStateData()))
                .build();
    }

    /**
     * 解析技能槽位 JSON；解析失败时返回空状态，让上层安全地重新收集信息。
     */
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
