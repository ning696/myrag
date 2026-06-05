package com.zc.iflyzcragback.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolParamDefinition {
    private String key;
    private String label;
    private String type;
    private String description;
    private Object defaultValue;
    private Object value;
    private Boolean overridden;
    private List<String> options;
    private Double min;
    private Double max;
}
