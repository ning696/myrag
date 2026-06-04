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
/**
 * 切块参数。
 *
 * <p>前端可让用户调整 chunk 大小、重叠长度和切块策略。参数会影响检索召回和回答准确率。</p>
 */
public class ChunkParams {

    @Min(value = 100, message = "chunk_size 至少 100")
    @Max(value = 2000, message = "chunk_size 最大 2000")
    /** 每个 chunk 的目标 token 数。 */
    private Integer size = 800;

    @Min(value = 0, message = "overlap 不能为负")
    @Max(value = 300, message = "overlap 最大 300")
    /** 相邻 chunk 的重叠 token 数，用于保留上下文。 */
    private Integer overlap = 80;

    @NotBlank(message = "strategy 不能为空")
    /** 切块策略：RECURSIVE 通用递归切分，BY_HEADING 按 Markdown 标题切分。 */
    private String strategy = "RECURSIVE";  // RECURSIVE / BY_HEADING
}
