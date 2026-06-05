package com.zc.iflyzcragback.service.rag.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zc.iflyzcragback.common.BizException;
import com.zc.iflyzcragback.dto.ToolVO;
import com.zc.iflyzcragback.entity.ToolConfigEntity;
import com.zc.iflyzcragback.mapper.ToolConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ToolService {

    private final List<ManagedTool> toolBeans;
    private final ToolConfigMapper configMapper;

    @Transactional
    public List<ToolVO> list() {
        Map<String, ManagedTool> registered = registeredTools();
        for (ManagedTool tool : registered.values()) {
            ensureConfig(tool);
        }

        Map<String, ToolConfigEntity> configs = configMapper.selectList(new LambdaQueryWrapper<ToolConfigEntity>())
                .stream()
                .collect(Collectors.toMap(ToolConfigEntity::getToolName, c -> c, (a, b) -> a, LinkedHashMap::new));

        List<ToolVO> result = new ArrayList<>();
        for (ManagedTool tool : registered.values()) {
            result.add(toVO(tool, configs.get(tool.name())));
        }
        for (ToolConfigEntity config : configs.values()) {
            if (!registered.containsKey(config.getToolName())) {
                result.add(toVO(null, config));
            }
        }
        result.sort(Comparator.comparing(ToolVO::getToolName));
        return result;
    }

    public List<ManagedTool> enabledTools() {
        Map<String, ManagedTool> registered = registeredTools();
        Map<String, ToolConfigEntity> configs = configMapper.selectList(new LambdaQueryWrapper<ToolConfigEntity>())
                .stream()
                .collect(Collectors.toMap(ToolConfigEntity::getToolName, c -> c, (a, b) -> a));
        return registered.values().stream()
                .filter(tool -> {
                    ToolConfigEntity config = configs.get(tool.name());
                    return config == null || config.enabledAsBoolean();
                })
                .toList();
    }

    @Transactional
    public ToolVO toggle(String toolName, boolean enabled) {
        ManagedTool tool = registeredTools().get(toolName);
        if (tool == null) {
            throw new BizException("工具不存在: " + toolName);
        }
        ToolConfigEntity config = ensureConfig(tool);
        config.setEnabled(enabled ? 1 : 0);
        configMapper.updateById(config);
        return toVO(tool, config);
    }

    private Map<String, ManagedTool> registeredTools() {
        return toolBeans.stream()
                .collect(Collectors.toMap(ManagedTool::name, t -> t, (a, b) -> a, LinkedHashMap::new));
    }

    private ToolConfigEntity ensureConfig(ManagedTool tool) {
        ToolConfigEntity config = configMapper.selectOne(new LambdaQueryWrapper<ToolConfigEntity>()
                .eq(ToolConfigEntity::getToolName, tool.name()));
        if (config != null) {
            boolean changed = false;
            if (!tool.displayName().equals(config.getDisplayName())) {
                config.setDisplayName(tool.displayName());
                changed = true;
            }
            if (!tool.description().equals(config.getDescription())) {
                config.setDescription(tool.description());
                changed = true;
            }
            if (changed) {
                configMapper.updateById(config);
            }
            return config;
        }
        ToolConfigEntity created = new ToolConfigEntity();
        created.setToolName(tool.name());
        created.setDisplayName(tool.displayName());
        created.setDescription(tool.description());
        created.setEnabled(1);
        configMapper.insert(created);
        return created;
    }

    private ToolVO toVO(ManagedTool tool, ToolConfigEntity config) {
        String toolName = tool != null ? tool.name() : config.getToolName();
        return ToolVO.builder()
                .toolName(toolName)
                .displayName(tool != null ? tool.displayName() : config.getDisplayName())
                .description(tool != null ? tool.description() : config.getDescription())
                .enabled(config != null && config.enabledAsBoolean())
                .available(tool != null && tool.available())
                .build();
    }
}
