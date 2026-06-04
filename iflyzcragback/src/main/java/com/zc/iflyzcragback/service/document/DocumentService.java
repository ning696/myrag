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
/**
 * 文档业务服务。
 *
 * <p>这个类负责知识库文档的完整生命周期：上传文件、解析文本、切块预览、
 * 确认入库、向量化、查看进度、列表展示和删除。对于智能对话系统来说，
 * 这里是“把用户资料变成可检索知识”的入口。</p>
 */
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

    /**
     * 上传文档并生成切块预览。
     *
     * <p>注意：上传后并不会立刻写入向量库，而是先保存到 MinIO、解析文本、生成预览。
     * 用户确认切块效果后，再调用 confirmIngestAsync 正式入库。</p>
     */
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

    /**
     * 根据用户调整后的参数重新切块。
     *
     * <p>这一步只更新 Redis 中的预览数据，不会影响已经入库的向量。</p>
     */
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
    /**
     * 确认入库：把预览 chunk 写入 MySQL，并向量化后写入 Milvus。
     *
     * <p>这是耗时操作，所以使用 @Async 放到线程池执行。前端可以轮询 getIngestProgress
     * 查看处理进度。</p>
     */
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
                // MySQL 保存 chunk 原文和可展示信息，方便列表、引用、BM25 关键词检索。
                DocumentChunkEntity entity = new DocumentChunkEntity();
                entity.setDocumentId(documentId);
                entity.setUserId(userId);
                entity.setChunkIndex(preview.getChunkIndex());
                entity.setContent(preview.getContent());
                entity.setTitle(preview.getTitle());
                entity.setKeywords(preview.getKeywords() == null ? null : String.join(",", preview.getKeywords()));
                chunkEntities.add(entity);

                // 构造 TextSegment（含 metadata）
                // metadata 会跟着向量一起写入 Milvus。检索时可用 userId 做数据隔离，
                // 回答时可用 documentName/chunkIndex/title 生成来源引用。
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
                // 批量大小过大会慢或超时，过小会增加请求次数；由 rag.ingest.embed-batch-size 控制。
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

    /**
     * 对一批 chunk 做向量化，并分别写入 MySQL 与 Milvus。
     */
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

    /**
     * 将入库进度写入 Redis。Redis 设置 TTL，避免进度 key 永久占用空间。
     */
    private void updateProgress(String key, String status, int processed, int total) {
        IngestProgressVO progress = new IngestProgressVO(status, processed, total);
        redisTemplate.opsForValue().set(key, progress, props.getIngest().getProgressTtlSeconds(), TimeUnit.SECONDS);
    }

    /**
     * 查询异步入库进度，供前端轮询展示。
     */
    public IngestProgressVO getIngestProgress(Long documentId) {
        String key = INGEST_PROGRESS_KEY + documentId;
        Object obj = redisTemplate.opsForValue().get(key);
        if (obj == null) {
            return new IngestProgressVO("not_found", 0, 0);
        }
        return (IngestProgressVO) obj;
    }

    /**
     * 分页查询当前用户的文档列表。
     */
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
    /**
     * 删除文档及其 chunk。
     *
     * <p>这里会删除 MySQL 记录、MinIO 文件和预览缓存。Milvus 删除当前仍保留 TODO，
     * 后续应补充按 documentId metadata 过滤删除向量的逻辑。</p>
     */
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

    /**
     * Entity 转 VO，只把前端需要看的字段返回出去。
     */
    private DocumentVO toVO(DocumentEntity entity) {
        DocumentVO vo = new DocumentVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    /**
     * 校验上传文件是否合法。
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("文件为空");
        }
        String ext = getExtension(file.getOriginalFilename());
        if (!List.of("pdf", "txt", "md", "markdown").contains(ext.toLowerCase())) {
            throw new BizException("仅支持 PDF、TXT、Markdown 格式");
        }
    }

    /**
     * 从文件名中提取扩展名，例如 report.pdf -> pdf。
     */
    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    /**
     * 获取上传文件输入流，并把底层异常转换成业务异常，方便统一返回给前端。
     */
    private InputStream getInputStream(MultipartFile file) {
        try {
            return file.getInputStream();
        } catch (Exception e) {
            throw new BizException("读取文件失败");
        }
    }
}
