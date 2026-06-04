package com.zc.iflyzcragback.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * 文档上传响应。
 *
 * <p>上传后返回文档 ID、实际使用的切块参数和 chunk 预览。</p>
 */
public class UploadResponse {
    /** 文档 ID。 */
    private Long documentId;
    /** 本次切块参数。 */
    private ChunkParams params;
    /** 切块预览列表。 */
    private List<ChunkPreviewVO> chunks;
}
