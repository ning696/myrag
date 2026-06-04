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
    /** 来源类型：document / web。 */
    private String sourceType;
    /** 网页标题。 */
    private String title;
    /** 网页 URL。 */
    private String url;
    /** 网页发布时间。 */
    private String publishedDate;

    public CitationVO(Integer n, Long documentId, String documentName,
                      Integer chunkIndex, String content, Double score) {
        this.n = n;
        this.documentId = documentId;
        this.documentName = documentName;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.score = score;
        this.sourceType = "document";
    }
}
