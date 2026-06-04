package com.zc.iflyzcragback.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * chunk 预览 VO。
 *
 * <p>文档上传后，前端先展示这些预览片段，让用户确认切块是否合理。</p>
 */
public class ChunkPreviewVO {
    /** chunk 序号。 */
    private Integer chunkIndex;
    /** chunk 原文内容。 */
    private String content;
    /** 自动提取的标题或短摘要。 */
    private String title;
    /** 自动提取的关键词。 */
    private List<String> keywords;
}
