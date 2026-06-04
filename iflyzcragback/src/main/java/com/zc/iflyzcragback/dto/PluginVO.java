package com.zc.iflyzcragback.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * 插件配置展示对象。
 */
public class PluginVO {
    private String pluginName;
    private String description;
    private Boolean enabled;
    private String hookType;
    private Integer priority;
    private String configJson;
    private Boolean registered;
}
