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
/**
 * MinIO 文件存储实现。
 *
 * <p>MinIO 可以理解为私有化对象存储。这里用它保存用户上传的原始文档，
 * 后续重新切块或删除文档时都通过 objectKey 定位文件。</p>
 */
public class MinioFileStorageService implements FileStorageService {

    private final MinioClient minioClient;
    private final MinioProperties properties;

    @Override
    /**
     * 上传文件到 MinIO，并返回对象 key。
     */
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
    /**
     * 从 MinIO 下载文件。调用方负责关闭返回的输入流。
     */
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
    /**
     * 删除 MinIO 对象。对象不存在时只记录日志，不影响业务流程。
     */
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

    /**
     * 生成对象 key。
     *
     * <p>格式为 userId/随机UUID-原文件名。userId 让文件天然按用户分目录，
     * UUID 避免两个同名文件互相覆盖。</p>
     */
    private String buildObjectKey(Long userId, String originalName) {
        String safeName = originalName == null ? "file" : originalName.replaceAll("[\\\\/]", "_");
        return userId + "/" + UUID.randomUUID() + "-" + safeName;
    }
}
