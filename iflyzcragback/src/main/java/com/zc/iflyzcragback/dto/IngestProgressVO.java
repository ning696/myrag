package com.zc.iflyzcragback.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * 文档入库进度 VO。
 *
 * <p>confirmIngestAsync 异步执行时，前端通过这个对象知道处理到哪里了。</p>
 */
public class IngestProgressVO {
    /** 当前状态：processing / completed / failed / not_found。 */
    private String status;  // processing / completed / failed
    /** 已处理 chunk 数。 */
    private Integer processed;
    /** 总 chunk 数。 */
    private Integer total;
}
