package com.zc.iflyzcragback.service.document;

import com.huaban.analysis.jieba.JiebaSegmenter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class KeywordExtractor {

    private final JiebaSegmenter segmenter = new JiebaSegmenter();
    private static final Set<String> STOP_WORDS = Set.of(
            "的", "了", "在", "是", "我", "有", "和", "就", "不", "人",
            "都", "一", "一个", "上", "也", "很", "到", "说", "要", "去",
            "你", "会", "着", "没有", "看", "好", "自己", "这"
    );

    public List<String> extract(String text, int topN) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> words = segmenter.sentenceProcess(text);
        Map<String, Integer> freq = new HashMap<>();
        for (String w : words) {
            if (w.length() < 2 || STOP_WORDS.contains(w) || !w.matches(".*[一-龥a-zA-Z].*")) {
                continue;
            }
            freq.merge(w, 1, Integer::sum);
        }

        return freq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(topN)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
}
