package com.zc.iflyzcragback.service.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * Embedding 服务。
 *
 * <p>Embedding 可以理解为“把文字翻译成一串数字坐标”。语义相近的句子，
 * 在向量空间里的距离也会更近。RAG 的检索阶段就是依赖这些向量来找相似文档片段。</p>
 */
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    /**
     * 将单段文本转成向量，常用于用户问题检索。
     */
    public Embedding embed(String text) {
        return embeddingModel.embed(text).content();
    }

    /**
     * 批量将多段文本转成向量，常用于文档入库时一次处理多个 chunk。
     */
    public List<Embedding> embedAll(List<String> texts) {
        List<TextSegment> segments = texts.stream().map(TextSegment::from).toList();
        return embeddingModel.embedAll(segments).content();
    }
}
