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
public class PreviewSessionService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RagProperties props;
    private final ObjectMapper objectMapper;

    private static final String PREVIEW_KEY = "rag:preview:";

    public void save(Long documentId, ChunkParams params, List<ChunkPreviewVO> chunks) {
        String key = PREVIEW_KEY + documentId;
        Map<String, Object> data = new HashMap<>();
        data.put("params", params);
        data.put("chunks", chunks);
        redisTemplate.opsForValue().set(key, data, props.getPreview().getTtlSeconds(), TimeUnit.SECONDS);
        log.info("预览数据已缓存: documentId={}, chunks={}", documentId, chunks.size());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> load(Long documentId) {
        String key = PREVIEW_KEY + documentId;
        Object obj = redisTemplate.opsForValue().get(key);
        if (obj == null) {
            return null;
        }
        return objectMapper.convertValue(obj, new TypeReference<>() {});
    }

    public void delete(Long documentId) {
        redisTemplate.delete(PREVIEW_KEY + documentId);
    }

    @SuppressWarnings("unchecked")
    public List<ChunkPreviewVO> getChunks(Long documentId) {
        Map<String, Object> data = load(documentId);
        if (data == null || !data.containsKey("chunks")) {
            return null;
        }
        return objectMapper.convertValue(data.get("chunks"), new TypeReference<>() {});
    }

    public ChunkParams getParams(Long documentId) {
        Map<String, Object> data = load(documentId);
        if (data == null || !data.containsKey("params")) {
            return null;
        }
        return objectMapper.convertValue(data.get("params"), ChunkParams.class);
    }
}
