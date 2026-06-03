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
public class DocumentParserDispatcher {

    private final ApachePdfBoxDocumentParser pdfParser = new ApachePdfBoxDocumentParser();

    public String parse(MultipartFile file) {
        String ext = getExtension(file.getOriginalFilename());
        try (InputStream in = file.getInputStream()) {
            return switch (ext.toLowerCase()) {
                case "pdf" -> pdfParser.parse(in).text();
                case "txt", "md", "markdown" -> new String(in.readAllBytes(), StandardCharsets.UTF_8);
                default -> throw new BizException("不支持的文件类型: " + ext);
            };
        } catch (IOException e) {
            throw new BizException("文件解析失败: " + e.getMessage());
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}
