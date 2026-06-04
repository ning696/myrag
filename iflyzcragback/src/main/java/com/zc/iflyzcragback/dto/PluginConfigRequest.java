package com.zc.iflyzcragback.dto;

import lombok.Data;

@Data
public class PluginConfigRequest {
    private String configJson;
    private String hookType;
    private Integer priority;
}
