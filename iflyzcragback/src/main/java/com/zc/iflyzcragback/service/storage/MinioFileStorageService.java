package com.zc.iflyzcragback.service.storage;

import com.zc.iflyzcragback.config.MinioProperties;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "minio", name = "enabled", havingValue = "true")
public class MinioFileStorageService implements FileStorageService {

    private final MinioClient minioClient;
    private final MinioProperties properties;

    @Override
    public String upload(Long userId, String originalName, String contentType, long size, InputStream input) {
        String objectKey = buildObjectKey(userId, originalName);
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucketName())
                    .object(objectKey)
                    .stream(input, size, -1)
                    .contentType(contentType != null ? contentType : "application/octet-stream")
                    .build());
            return objectKey;
        } catch (Exception e) {
            throw new IllegalStateException("MinIO 上传失败: " + objectKey, e);
        }
    }

    @Override
    public InputStream download(String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(properties.getBucketName())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("MinIO 下载失败: " + objectKey, e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getBucketName())
                    .object(objectKey)
                    .build());
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                log.warn("MinIO 对象不存在，跳过删除: {}", objectKey);
                return;
            }
            throw new IllegalStateException("MinIO 删除失败: " + objectKey, e);
        } catch (Exception e) {
            throw new IllegalStateException("MinIO 删除失败: " + objectKey, e);
        }
    }

    private String buildObjectKey(Long userId, String originalName) {
        String safeName = originalName == null ? "file" : originalName.replaceAll("[\\\\/]", "_");
        return userId + "/" + UUID.randomUUID() + "-" + safeName;
    }
}
