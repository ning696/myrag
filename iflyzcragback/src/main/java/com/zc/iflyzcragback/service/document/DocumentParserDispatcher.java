package com.zc.iflyzcragback.service.document;

import com.zc.iflyzcragback.common.BizException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
/**
 * 文档解析分发器。
 *
 * <p>不同格式的文件解析方式不同：PDF 需要 PDFBox，TXT/Markdown 可以直接按 UTF-8 读取。
 * 这个类把“文件格式判断”和“解析逻辑选择”集中在一起，DocumentService 不需要关心底层细节。</p>
 */
public class DocumentParserDispatcher {

    // PDF 解析器复用一个实例即可，避免每次解析都重复创建对象。
    private final ApachePdfBoxDocumentParser pdfParser = new ApachePdfBoxDocumentParser();

    /**
     * 将上传文件解析成纯文本。后续切块、关键词提取、向量化都基于这个纯文本。
     */
    public String parse(MultipartFile file) {
        String ext = getExtension(file.getOriginalFilename());
        try (InputStream in = file.getInputStream()) {
            return switch (ext.toLowerCase()) {
                // PDF 走 PDFBox；文本类文件直接读取字节并按 UTF-8 解码。
                case "pdf" -> pdfParser.parse(in).text();
                case "txt", "md", "markdown" -> new String(in.readAllBytes(), StandardCharsets.UTF_8);
                default -> throw new BizException("不支持的文件类型: " + ext);
            };
        } catch (IOException e) {
            throw new BizException("文件解析失败: " + e.getMessage());
        }
    }

    /**
     * 提取文件扩展名，并统一交给调用方做小写判断。
     */
    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}
