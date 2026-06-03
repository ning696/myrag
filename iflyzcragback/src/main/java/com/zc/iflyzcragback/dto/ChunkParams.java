package com.zc.iflyzcragback.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChunkParams {

    @Min(value = 100, message = "chunk_size 至少 100")
    @Max(value = 2000, message = "chunk_size 最大 2000")
    private Integer size = 800;

    @Min(value = 0, message = "overlap 不能为负")
    @Max(value = 300, message = "overlap 最大 300")
    private Integer overlap = 80;

    @NotBlank(message = "strategy 不能为空")
    private String strategy = "RECURSIVE";  // RECURSIVE / BY_HEADING
}
