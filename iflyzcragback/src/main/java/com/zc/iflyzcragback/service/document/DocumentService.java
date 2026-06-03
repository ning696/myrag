package com.zc.iflyzcragback.service.document;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zc.iflyzcragback.common.BizException;
import com.zc.iflyzcragback.config.RagProperties;
import com.zc.iflyzcragback.dto.*;
import com.zc.iflyzcragback.entity.DocumentChunkEntity;
import com.zc.iflyzcragback.entity.DocumentEntity;
import com.zc.iflyzcragback.mapper.DocumentChunkMapper;
import com.zc.iflyzcragback.mapper.DocumentMapper;
import com.zc.iflyzcragback.service.storage.FileStorageService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper chunkMapper;
    private final FileStorageService fileStorage;
    private final DocumentParserDispatcher parser;
    private final ChunkService chunkService;
    private final PreviewSessionService previewSession;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final RagProperties props;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String INGEST_PROGRESS_KEY = "rag:ingest:";

    public UploadResponse upload(MultipartFile file, Long userId) {
        validateFile(file);

        // 上传文件到 MinIO
        String objectKey = fileStorage.upload(userId, file.getOriginalFilename(),
                file.getContentType(), file.getSize(), getInputStream(file));

        // 解析文档全文
        String fullText = parser.parse(file);

        // 写入 documents 表，状态 processing
        DocumentEntity doc = new DocumentEntity();
        doc.setUserId(userId);
        doc.setFilename(file.getOriginalFilename());
        doc.setFileType(getExtension(file.getOriginalFilename()));
        doc.setFileSize(file.getSize());
        doc.setStatus("processing");
        doc.setVectorStoreId(objectKey);
        documentMapper.insert(doc);

        // 默认参数切块
        ChunkParams params = new ChunkParams(
                props.getChunk().getSize(),
                props.getChunk().getOverlap(),
                props.getChunk().getDefaultStrategy());
        List<ChunkPreviewVO> chunks = chunkService.split(fullText, params, doc.getFileType());

        // 存入 Redis 预览态
        previewSession.save(doc.getId(), params, chunks);

        log.info("文档上传成功: documentId={}, filename={}, chunks={}",
                doc.getId(), doc.getFilename(), chunks.size());
        return new UploadResponse(doc.getId(), params, chunks);
    }

    public UploadResponse rechunk(Long documentId, ChunkParams params, Long userId) {
        DocumentEntity doc = documentMapper.selectById(documentId);
        if (doc == null || !doc.getUserId().equals(userId)) {
            throw new BizException("文档不存在");
        }

        // 从 MinIO 读原文件重新解析
        try (InputStream in = fileStorage.download(doc.getVectorStoreId())) {
            String fullText = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            List<ChunkPreviewVO> chunks = chunkService.split(fullText, params, doc.getFileType());
            previewSession.save(documentId, params, chunks);
            log.info("文档重切完成: documentId={}, chunks={}", documentId, chunks.size());
            return new UploadResponse(documentId, params, chunks);
        } catch (Exception e) {
            throw new BizException("重新切块失败: " + e.getMessage());
        }
    }

    @Async("ingestExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void confirmIngestAsync(Long documentId, Long userId) {
        String progressKey = INGEST_PROGRESS_KEY + documentId;
        try {
            DocumentEntity doc = documentMapper.selectById(documentId);
            if (doc == null || !doc.getUserId().equals(userId)) {
                throw new BizException("文档不存在");
            }

            List<ChunkPreviewVO> chunks = previewSession.getChunks(documentId);
            if (chunks == null || chunks.isEmpty()) {
                throw new BizException("预览数据已过期");
            }

            int total = chunks.size();
            updateProgress(progressKey, "processing", 0, total);

            // 批量入库 document_chunks + 向量化 + 写 Milvus
            List<DocumentChunkEntity> chunkEntities = new ArrayList<>();
            List<TextSegment> segments = new ArrayList<>();

            for (int i = 0; i < chunks.size(); i++) {
                ChunkPreviewVO preview = chunks.get(i);
                DocumentChunkEntity entity = new DocumentChunkEntity();
                entity.setDocumentId(documentId);
                entity.setUserId(userId);
                entity.setChunkIndex(preview.getChunkIndex());
                entity.setContent(preview.getContent());
                entity.setTitle(preview.getTitle());
                entity.setKeywords(preview.getKeywords() == null ? null : String.join(",", preview.getKeywords()));
                chunkEntities.add(entity);

                // 构造 TextSegment（含 metadata）
                Metadata meta = Metadata.from(Map.of(
                        "userId", userId.toString(),
                        "documentId", documentId.toString(),
                        "documentName", doc.getFilename(),
                        "documentType", doc.getFileType(),
                        "chunkIndex", String.valueOf(preview.getChunkIndex()),
                        "title", preview.getTitle() == null ? "" : preview.getTitle(),
                        "keywords", preview.getKeywords() == null ? "" : String.join(",", preview.getKeywords())
                ));
                segments.add(TextSegment.from(preview.getContent(), meta));

                // 分批向量化
                if ((i + 1) % props.getIngest().getEmbedBatchSize() == 0 || i == chunks.size() - 1) {
                    embedAndStore(segments, chunkEntities, i + 1);
                    updateProgress(progressKey, "processing", i + 1, total);
                    segments.clear();
                    chunkEntities.clear();
                }
            }

            // 更新 documents 状态
            doc.setStatus("completed");
            doc.setChunkCount(total);
            doc.setEmbeddingVersion(props.getEmbedding().getVersion());
            documentMapper.updateById(doc);

            // 删除预览缓存
            previewSession.delete(documentId);

            updateProgress(progressKey, "completed", total, total);
            log.info("文档入库完成: documentId={}, chunks={}", documentId, total);
        } catch (Exception e) {
            log.error("文档入库失败: documentId={}", documentId, e);
            DocumentEntity doc = documentMapper.selectById(documentId);
            if (doc != null) {
                doc.setStatus("failed");
                doc.setErrorMessage(e.getMessage());
                documentMapper.updateById(doc);
            }
            updateProgress(progressKey, "failed", 0, 0);
        }
    }

    private void embedAndStore(List<TextSegment> segments, List<DocumentChunkEntity> entities, int processed) {
        if (segments.isEmpty()) return;

        // 批量向量化
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

        // 批量写 MySQL
        for (DocumentChunkEntity e : entities) {
            chunkMapper.insert(e);
        }

        // 批量写 Milvus（带 metadata）
        embeddingStore.addAll(embeddings, segments);

        // 回写 vector_id 到 MySQL（可选，用于跨库定位）
        for (int i = 0; i < entities.size(); i++) {
            DocumentChunkEntity e = entities.get(i);
            e.setVectorId(e.getDocumentId() + "-" + e.getChunkIndex());
            chunkMapper.updateById(e);
        }
    }

    private void updateProgress(String key, String status, int processed, int total) {
        IngestProgressVO progress = new IngestProgressVO(status, processed, total);
        redisTemplate.opsForValue().set(key, progress, props.getIngest().getProgressTtlSeconds(), TimeUnit.SECONDS);
    }

    public IngestProgressVO getIngestProgress(Long documentId) {
        String key = INGEST_PROGRESS_KEY + documentId;
        Object obj = redisTemplate.opsForValue().get(key);
        if (obj == null) {
            return new IngestProgressVO("not_found", 0, 0);
        }
        return (IngestProgressVO) obj;
    }

    public Page<DocumentVO> list(Long userId, int page, int size) {
        Page<DocumentEntity> p = documentMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<DocumentEntity>()
                        .eq(DocumentEntity::getUserId, userId)
                        .orderByDesc(DocumentEntity::getUploadTime));
        Page<DocumentVO> result = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        result.setRecords(p.getRecords().stream().map(this::toVO).toList());
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long documentId, Long userId) {
        DocumentEntity doc = documentMapper.selectById(documentId);
        if (doc == null || !doc.getUserId().equals(userId)) {
            throw new BizException("文档不存在");
        }

        // 删除 Milvus（按 documentId metadata 过滤删除）
        // TODO: Milvus 删除需用 expr，暂用逻辑删除

        // 删除 document_chunks
        chunkMapper.delete(new LambdaQueryWrapper<DocumentChunkEntity>()
                .eq(DocumentChunkEntity::getDocumentId, documentId));

        // 删除 MinIO
        fileStorage.delete(doc.getVectorStoreId());

        // 删除 documents
        documentMapper.deleteById(documentId);

        // 删除预览缓存
        previewSession.delete(documentId);

        log.info("文档删除成功: documentId={}", documentId);
    }

    private DocumentVO toVO(DocumentEntity entity) {
        DocumentVO vo = new DocumentVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("文件为空");
        }
        String ext = getExtension(file.getOriginalFilename());
        if (!List.of("pdf", "txt", "md", "markdown").contains(ext.toLowerCase())) {
            throw new BizException("仅支持 PDF、TXT、Markdown 格式");
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    private InputStream getInputStream(MultipartFile file) {
        try {
            return file.getInputStream();
        } catch (Exception e) {
            throw new BizException("读取文件失败");
        }
    }
}
