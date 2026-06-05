package com.zc.iflyzcragback.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SkillToggleRequest {
    @NotNull
    private Boolean enabled;
}
