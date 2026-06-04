package com.zc.iflyzcragback.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * 回答引用来源 VO。
 *
 * <p>RAG 回答需要告诉用户答案依据来自哪个文档片段，前端可用它展示来源卡片。</p>
 */
public class CitationVO {
    /** 来源编号，对应回答中的 [来源 N]。 */
    private Integer n;
    /** 文档 ID。 */
    private Long documentId;
    /** 文档名称。 */
    private String documentName;
    /** chunk 在文档中的序号。 */
    private Integer chunkIndex;
    /** 被引用的 chunk 内容。 */
    private String content;
    /** 检索分数。 */
    private Double score;
}
