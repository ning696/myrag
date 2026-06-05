package com.zc.iflyzcragback.service.rag.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebSearchSource {
    private Integer index;
    private String title;
    private String url;
    private String content;
    private Double score;
    private String publishedDate;
}
