package com.zc.iflyzcragback.service.document;

import com.zc.iflyzcragback.common.BizException;
import com.zc.iflyzcragback.config.RagProperties;
import com.zc.iflyzcragback.dto.ChunkParams;
import com.zc.iflyzcragback.dto.ChunkPreviewVO;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkService {

    private static final String TOKENIZER_MODEL = "gpt-4o-mini";
    private static final OpenAiTokenizer TOKENIZER = new OpenAiTokenizer(TOKENIZER_MODEL);

    private final RagProperties props;
    private final KeywordExtractor keywordExtractor;

    public List<ChunkPreviewVO> split(String text, ChunkParams params, String fileType) {
        if (text == null || text.isBlank()) {
            throw new BizException("文档内容为空");
        }

        String strategy = params.getStrategy();
        if (strategy == null || strategy.isBlank()) {
            strategy = props.getChunk().getDefaultStrategy();
        }

        List<TextSegment> segments;
        if ("BY_HEADING".equalsIgnoreCase(strategy) && isMarkdown(fileType)) {
            segments = splitByHeading(text, params.getSize(), params.getOverlap());
        } else {
            segments = splitRecursive(text, params.getSize(), params.getOverlap());
        }

        List<ChunkPreviewVO> result = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            TextSegment seg = segments.get(i);
            String content = seg.text();
            String title = extractTitle(content);
            List<String> keywords = keywordExtractor.extract(content, 5);
            result.add(new ChunkPreviewVO(i, content, title, keywords));
        }
        log.info("文档切分完成: strategy={}, chunks={}", strategy, result.size());
        return result;
    }

    private List<TextSegment> splitRecursive(String text, int size, int overlap) {
        DocumentSplitter splitter = DocumentSplitters.recursive(size, overlap, TOKENIZER);
        return splitter.split(Document.from(text));
    }

    private List<TextSegment> splitByHeading(String text, int maxSize, int overlap) {
        Pattern pattern = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(text);

        List<TextSegment> segments = new ArrayList<>();
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                String chunk = text.substring(lastEnd, matcher.start()).trim();
                if (!chunk.isEmpty()) {
                    segments.add(TextSegment.from(chunk));
                }
            }
            lastEnd = matcher.start();
        }

        if (lastEnd < text.length()) {
            String chunk = text.substring(lastEnd).trim();
            if (!chunk.isEmpty()) {
                segments.add(TextSegment.from(chunk));
            }
        }

        if (segments.isEmpty()) {
            return splitRecursive(text, maxSize, overlap);
        }

        List<TextSegment> refined = new ArrayList<>();
        for (TextSegment seg : segments) {
            if (countTokens(seg.text()) > maxSize) {
                refined.addAll(splitRecursive(seg.text(), maxSize, overlap));
            } else {
                refined.add(seg);
            }
        }
        return refined;
    }

    private boolean isMarkdown(String fileType) {
        return fileType != null && (fileType.equalsIgnoreCase("md") || fileType.equalsIgnoreCase("markdown"));
    }

    private String extractTitle(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String[] lines = content.split("\n", 3);
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("#")) {
                return line.replaceFirst("^#+\\s*", "").trim();
            }
            if (line.length() > 5 && line.length() < 100) {
                return line;
            }
        }
        return content.length() > 50 ? content.substring(0, 50) + "..." : content;
    }

    private int countTokens(String text) {
        return TOKENIZER.estimateTokenCountInText(text);
    }
}
