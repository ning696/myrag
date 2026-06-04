package com.zc.iflyzcragback.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.iflyzcragback.common.BizException;
import com.zc.iflyzcragback.dto.PluginConfigRequest;
import com.zc.iflyzcragback.dto.PluginVO;
import com.zc.iflyzcragback.entity.PluginConfigEntity;
import com.zc.iflyzcragback.mapper.PluginConfigMapper;
import com.zc.iflyzcragback.plugin.Plugin;
import com.zc.iflyzcragback.plugin.PluginManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
/**
 * 插件配置管理服务。
 */
public class PluginService {

    private final PluginConfigMapper configMapper;
    private final PluginManager pluginManager;
    private final ObjectMapper objectMapper;

    public List<PluginVO> list() {
        Map<String, Plugin> registered = pluginManager.registeredPlugins();
        Map<String, PluginConfigEntity> configs = configMapper.selectList(
                        new LambdaQueryWrapper<PluginConfigEntity>())
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        PluginConfigEntity::getPluginName, c -> c, (a, b) -> a));

        List<PluginVO> result = new ArrayList<>();
        for (Plugin plugin : registered.values()) {
            PluginConfigEntity config = configs.get(plugin.getName());
            result.add(toVO(plugin, config));
        }
        for (PluginConfigEntity config : configs.values()) {
            if (!registered.containsKey(config.getPluginName())) {
                result.add(toVO(null, config));
            }
        }
        result.sort(Comparator.comparingInt((PluginVO p) ->
                p.getPriority() == null ? 0 : p.getPriority()).reversed());
        return result;
    }

    @Transactional
    public PluginVO toggle(String pluginName, boolean enabled) {
        PluginConfigEntity config = requireConfig(pluginName);
        config.setEnabled(enabled ? 1 : 0);
        configMapper.updateById(config);
        return toVO(pluginManager.registeredPlugins().get(pluginName), config);
    }

    @Transactional
    public PluginVO updateConfig(String pluginName, PluginConfigRequest request) {
        PluginConfigEntity config = requireConfig(pluginName);
        if (request.getConfigJson() != null) {
            validateNonSensitiveJson(request.getConfigJson());
            config.setConfigJson(request.getConfigJson());
        }
        if (request.getHookType() != null && !request.getHookType().isBlank()) {
            config.setHookType(validateHookType(request.getHookType()));
        }
        if (request.getPriority() != null) {
            config.setPriority(request.getPriority());
        }
        configMapper.updateById(config);
        return toVO(pluginManager.registeredPlugins().get(pluginName), config);
    }

    private PluginConfigEntity requireConfig(String pluginName) {
        PluginConfigEntity config = configMapper.selectOne(
                new LambdaQueryWrapper<PluginConfigEntity>()
                        .eq(PluginConfigEntity::getPluginName, pluginName));
        if (config == null) {
            throw new BizException("插件配置不存在: " + pluginName);
        }
        return config;
    }

    private PluginVO toVO(Plugin plugin, PluginConfigEntity config) {
        String name = plugin != null ? plugin.getName() : config.getPluginName();
        return PluginVO.builder()
                .pluginName(name)
                .description(plugin != null ? plugin.getDescription() : config.getDescription())
                .enabled(config != null && config.isEnabled())
                .hookType(config == null ? null : config.getHookType())
                .priority(config == null ? null : config.getPriority())
                .configJson(config == null ? null : config.getConfigJson())
                .registered(plugin != null)
                .build();
    }

    private String validateHookType(String hookType) {
        String normalized = hookType.trim().toLowerCase();
        if (!List.of("before", "after", "both").contains(normalized)) {
            throw new BizException("hookType 只能是 before / after / both");
        }
        return normalized;
    }

    private void validateNonSensitiveJson(String configJson) {
        if (configJson.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(configJson);
            rejectSensitiveKeys(root);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("configJson 必须是合法 JSON");
        }
    }

    private void rejectSensitiveKeys(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            Iterator<String> fields = node.fieldNames();
            while (fields.hasNext()) {
                String field = fields.next();
                String normalized = field.toLowerCase();
                if (normalized.contains("apikey")
                        || normalized.contains("api_key")
                        || normalized.contains("secret")
                        || normalized.contains("token")
                        || normalized.contains("password")) {
                    throw new BizException("插件配置不能保存密钥、token 或密码，请使用环境变量");
                }
                rejectSensitiveKeys(node.get(field));
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                rejectSensitiveKeys(child);
            }
        }
    }
}
