package com.zc.iflyzcragback.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "minio", name = "enabled", havingValue = "true")
/**
 * MinIO 客户端配置。
 *
 * <p>项目启动时创建 MinioClient，并确保目标 bucket 存在。</p>
 */
public class MinioConfig {

    private final MinioProperties properties;

    @Bean
    /**
     * 创建 MinIO 客户端。
     */
    public MinioClient minioClient() throws Exception {
        MinioClient client = MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();

        String bucket = properties.getBucketName();
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            // bucket 不存在时自动创建，减少本地开发和部署初始化成本。
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("MinIO bucket created: {}", bucket);
        } else {
            log.info("MinIO bucket exists: {}", bucket);
        }
        return client;
    }
}
