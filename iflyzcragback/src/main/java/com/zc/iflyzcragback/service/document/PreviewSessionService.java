package com.zc.iflyzcragback.service.document;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zc.iflyzcragback.config.RagProperties;
import com.zc.iflyzcragback.dto.ChunkParams;
import com.zc.iflyzcragback.dto.ChunkPreviewVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * 切块预览缓存服务。
 *
 * <p>用户上传文档后，系统会先把切好的 chunk 暂存在 Redis，让前端展示预览。
 * 用户确认后才正式写入 MySQL 和 Milvus。这样可以避免错误切块直接污染知识库。</p>
 */
public class PreviewSessionService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RagProperties props;
    private final ObjectMapper objectMapper;

    private static final String PREVIEW_KEY = "rag:preview:";

    /**
     * 保存某个文档的切块参数和预览 chunk。
     */
    public void save(Long documentId, ChunkParams params, List<ChunkPreviewVO> chunks) {
        String key = PREVIEW_KEY + documentId;
        Map<String, Object> data = new HashMap<>();
        data.put("params", params);
        data.put("chunks", chunks);
        redisTemplate.opsForValue().set(key, data, props.getPreview().getTtlSeconds(), TimeUnit.SECONDS);
        log.info("预览数据已缓存: documentId={}, chunks={}", documentId, chunks.size());
    }

    @SuppressWarnings("unchecked")
    /**
     * 读取 Redis 中的原始预览数据。
     *
     * <p>Redis 反序列化后可能是 Map/LinkedHashMap，这里统一用 ObjectMapper 转成可操作的 Map。</p>
     */
    public Map<String, Object> load(Long documentId) {
        String key = PREVIEW_KEY + documentId;
        Object obj = redisTemplate.opsForValue().get(key);
        if (obj == null) {
            return null;
        }
        return objectMapper.convertValue(obj, new TypeReference<>() {});
    }

    /**
     * 删除预览缓存，通常在确认入库或删除文档后调用。
     */
    public void delete(Long documentId) {
        redisTemplate.delete(PREVIEW_KEY + documentId);
    }

    @SuppressWarnings("unchecked")
    /**
     * 读取预览 chunk，供确认入库时使用。
     */
    public List<ChunkPreviewVO> getChunks(Long documentId) {
        Map<String, Object> data = load(documentId);
        if (data == null || !data.containsKey("chunks")) {
            return null;
        }
        return objectMapper.convertValue(data.get("chunks"), new TypeReference<>() {});
    }

    /**
     * 读取用户当时选择的切块参数。
     */
    public ChunkParams getParams(Long documentId) {
        Map<String, Object> data = load(documentId);
        if (data == null || !data.containsKey("params")) {
            return null;
        }
        return objectMapper.convertValue(data.get("params"), ChunkParams.class);
    }
}
