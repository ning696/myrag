package com.zc.iflyzcragback.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zc.iflyzcragback.common.Result;
import com.zc.iflyzcragback.dto.*;
import com.zc.iflyzcragback.security.SecurityUtils;
import com.zc.iflyzcragback.service.document.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/upload")
    public Result<UploadResponse> upload(@RequestParam("file") MultipartFile file) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(documentService.upload(file, userId));
    }

    @PostMapping("/{id}/rechunk")
    public Result<UploadResponse> rechunk(@PathVariable Long id,
                                          @Valid @RequestBody ChunkParams params) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(documentService.rechunk(id, params, userId));
    }

    @PostMapping("/{id}/confirm-ingest")
    public Result<Void> confirmIngest(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        documentService.confirmIngestAsync(id, userId);
        return Result.success();
    }

    @GetMapping("/{id}/ingest-progress")
    public Result<IngestProgressVO> getIngestProgress(@PathVariable Long id) {
        return Result.success(documentService.getIngestProgress(id));
    }

    @GetMapping
    public Result<Page<DocumentVO>> list(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(documentService.list(userId, page, size));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        documentService.delete(id, userId);
        return Result.success();
    }
}
