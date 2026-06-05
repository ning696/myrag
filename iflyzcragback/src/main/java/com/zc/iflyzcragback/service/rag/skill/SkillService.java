package com.zc.iflyzcragback.service.rag.skill;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zc.iflyzcragback.common.BizException;
import com.zc.iflyzcragback.dto.SkillVO;
import com.zc.iflyzcragback.entity.SkillConfigEntity;
import com.zc.iflyzcragback.mapper.SkillConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkillService {
    private final SkillRegistry registry;
    private final SkillConfigMapper configMapper;

    public List<SkillVO> list() {
        syncRegisteredSkills();
        List<SkillVO> result = new ArrayList<>();
        for (Skill skill : registry.list()) {
            SkillConfigEntity config = selectConfig(skill.name()).orElseGet(() -> defaultConfig(skill));
            result.add(toVO(skill, config));
        }
        result.sort(Comparator.comparing(SkillVO::getSkillName));
        return result;
    }

    public SkillVO toggle(String skillName, boolean enabled) {
        Skill skill = registry.find(skillName)
                .orElseThrow(() -> new BizException("技能不存在: " + skillName));
        SkillConfigEntity config = ensureConfig(skill);
        config.setEnabled(enabled ? 1 : 0);
        configMapper.updateById(config);
        log.info("Skill toggled | skillName={} | enabled={}", skillName, enabled);
        return toVO(skill, config);
    }

    public List<Skill> enabledSkills() {
        syncRegisteredSkills();
        return registry.list().stream()
                .filter(Skill::available)
                .filter(skill -> isEnabled(skill.name()))
                .toList();
    }

    public boolean isEnabled(String skillName) {
        try {
            return selectConfig(skillName)
                    .map(SkillConfigEntity::enabledAsBoolean)
                    .orElse(true);
        } catch (DataAccessException e) {
            log.warn("Skill config table unavailable, disable skill routing for now", e);
            return false;
        }
    }

    private void syncRegisteredSkills() {
        try {
            for (Skill skill : registry.list()) {
                ensureConfig(skill);
            }
        } catch (DataAccessException e) {
            log.warn("Skill config table unavailable, skip config sync", e);
        }
    }

    private SkillConfigEntity ensureConfig(Skill skill) {
        Optional<SkillConfigEntity> existing = selectConfig(skill.name());
        if (existing.isPresent()) {
            SkillConfigEntity config = existing.get();
            boolean changed = false;
            if (!skill.displayName().equals(config.getDisplayName())) {
                config.setDisplayName(skill.displayName());
                changed = true;
            }
            if (!skill.description().equals(config.getDescription())) {
                config.setDescription(skill.description());
                changed = true;
            }
            if (changed) {
                configMapper.updateById(config);
            }
            return config;
        }
        SkillConfigEntity created = defaultConfig(skill);
        configMapper.insert(created);
        return created;
    }

    private Optional<SkillConfigEntity> selectConfig(String skillName) {
        return Optional.ofNullable(configMapper.selectOne(new LambdaQueryWrapper<SkillConfigEntity>()
                .eq(SkillConfigEntity::getSkillName, skillName)));
    }

    private SkillConfigEntity defaultConfig(Skill skill) {
        SkillConfigEntity config = new SkillConfigEntity();
        config.setSkillName(skill.name());
        config.setDisplayName(skill.displayName());
        config.setDescription(skill.description());
        config.setEnabled(1);
        return config;
    }

    private SkillVO toVO(Skill skill, SkillConfigEntity config) {
        return SkillVO.builder()
                .skillName(skill.name())
                .displayName(skill.displayName())
                .description(skill.description())
                .enabled(config.enabledAsBoolean())
                .available(skill.available())
                .build();
    }
}
