package com.zc.iflyzcragback.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zc.iflyzcragback.common.Result;
import com.zc.iflyzcragback.dto.*;
import com.zc.iflyzcragback.security.SecurityUtils;
import com.zc.iflyzcragback.service.document.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
/**
 * 文档接口控制器。
 *
 * <p>负责接收前端的文档上传、重切、确认入库、进度查询、列表和删除请求。
 * 每个接口都会拿当前登录用户 ID，避免用户操作别人的文档。</p>
 */
public class DocumentController {

    private final DocumentService documentService;

    /**
     * 上传文档并返回切块预览。
     */
    @PostMapping("/upload")
    public Result<UploadResponse> upload(@RequestParam("file") MultipartFile file) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(documentService.upload(file, userId));
    }

    /**
     * 用新的切块参数重新生成预览。
     */
    @PostMapping("/{id}/rechunk")
    public Result<UploadResponse> rechunk(@PathVariable Long id,
                                          @Valid @RequestBody ChunkParams params) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(documentService.rechunk(id, params, userId));
    }

    /**
     * 用户确认预览后，异步执行向量化和入库。
     */
    @PostMapping("/{id}/confirm-ingest")
    public Result<Void> confirmIngest(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        documentService.confirmIngestAsync(id, userId);
        return Result.success();
    }

    /**
     * 查询文档入库进度，前端可以轮询这个接口。
     */
    @GetMapping("/{id}/ingest-progress")
    public Result<IngestProgressVO> getIngestProgress(@PathVariable Long id) {
        return Result.success(documentService.getIngestProgress(id));
    }

    /**
     * 分页查询当前用户上传的文档。
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        DocumentDownloadResource resource = documentService.download(id, userId);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(resource.filename(), StandardCharsets.UTF_8)
                        .build()
                        .toString());
        if (resource.fileSize() != null) {
            builder.contentLength(resource.fileSize());
        }
        return builder.body(new InputStreamResource(resource.inputStream()));
    }

    @GetMapping("/{id}/chunks")
    public Result<List<ChunkPreviewVO>> chunks(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(documentService.listChunks(id, userId));
    }

    @GetMapping
    public Result<Page<DocumentVO>> list(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(documentService.list(userId, page, size));
    }

    /**
     * 删除文档。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        documentService.delete(id, userId);
        return Result.success();
    }
}
