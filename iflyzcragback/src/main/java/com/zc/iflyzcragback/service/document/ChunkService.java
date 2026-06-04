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
/**
 * 文档切块服务。
 *
 * <p>RAG 不能把整篇文档一次性塞给大模型，因为上下文窗口有限，也会降低检索精度。
 * 因此上传文档后要先切成多个 chunk，每个 chunk 再生成向量入库。切块质量会直接影响
 * 后续“能不能搜到正确资料”。</p>
 */
public class ChunkService {

    // LangChain4j 的递归切分器需要 tokenizer 来估算 token 数，避免 chunk 超过模型上下文限制。
    private static final String TOKENIZER_MODEL = "gpt-4o-mini";
    private static final OpenAiTokenizer TOKENIZER = new OpenAiTokenizer(TOKENIZER_MODEL);

    private final RagProperties props;
    private final KeywordExtractor keywordExtractor;

    /**
     * 按指定参数切分文档，并生成前端预览需要的 chunk 信息。
     *
     * @param text 文档全文
     * @param params 切块大小、重叠长度和策略
     * @param fileType 文件类型，用于决定是否启用 Markdown 标题切分
     */
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
            // Markdown 文档通常有标题结构，按标题切能保留章节语义。
            segments = splitByHeading(text, params.getSize(), params.getOverlap());
        } else {
            // 普通 PDF/TXT 使用递归切分，优先按段落、句子等自然边界拆分。
            segments = splitRecursive(text, params.getSize(), params.getOverlap());
        }

        List<ChunkPreviewVO> result = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            TextSegment seg = segments.get(i);
            String content = seg.text();
            // title 和 keywords 会进入 metadata，帮助后续检索、展示和引用。
            String title = extractTitle(content);
            List<String> keywords = keywordExtractor.extract(content, 5);
            result.add(new ChunkPreviewVO(i, content, title, keywords));
        }
        log.info("文档切分完成: strategy={}, chunks={}", strategy, result.size());
        return result;
    }

    /**
     * 递归切块：先尝试按较大的语义边界切，太长时再逐步细分。
     */
    private List<TextSegment> splitRecursive(String text, int size, int overlap) {
        DocumentSplitter splitter = DocumentSplitters.recursive(size, overlap, TOKENIZER);
        return splitter.split(Document.from(text));
    }

    /**
     * Markdown 标题切块。
     *
     * <p>先按 # / ## / ### 等标题定位章节；如果某个章节仍然过长，再交给递归切分器二次拆分。</p>
     */
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
            // 没识别出标题时回退到通用切分，保证任意文本都能处理。
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

    /**
     * 判断是否为 Markdown 文件。
     */
    private boolean isMarkdown(String fileType) {
        return fileType != null && (fileType.equalsIgnoreCase("md") || fileType.equalsIgnoreCase("markdown"));
    }

    /**
     * 从 chunk 中提取一个可读标题，用于前端预览和来源展示。
     */
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

    /**
     * 估算文本 token 数。token 可以粗略理解为模型处理文本时的最小计费/上下文单位。
     */
    private int countTokens(String text) {
        return TOKENIZER.estimateTokenCountInText(text);
    }
}
