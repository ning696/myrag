package com.zc.iflyzcragback.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillVO {
    private String skillName;
    private String displayName;
    private String description;
    private Boolean enabled;
    private Boolean available;
}
