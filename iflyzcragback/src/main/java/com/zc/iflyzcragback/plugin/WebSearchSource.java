package com.zc.iflyzcragback.plugin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * 联网搜索来源。
 */
public class WebSearchSource {
    private Integer index;
    private String title;
    private String url;
    private String content;
    private Double score;
    private String publishedDate;
}
