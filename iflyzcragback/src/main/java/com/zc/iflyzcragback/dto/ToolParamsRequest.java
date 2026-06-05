package com.zc.iflyzcragback.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class ToolParamsRequest {
    @NotNull
    private Map<String, Object> params;
}
