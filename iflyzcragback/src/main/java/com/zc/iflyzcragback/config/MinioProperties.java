package com.zc.iflyzcragback.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "minio")
/**
 * MinIO 配置属性。
 *
 * <p>对应 application.yml 中的 minio.*，用于连接对象存储并保存上传文档原文件。</p>
 */
public class MinioProperties {
    /** 是否启用 MinIO 存储。 */
    private boolean enabled;
    /** MinIO 访问地址，例如 http://localhost:9000。 */
    private String endpoint;
    /** 访问密钥。 */
    private String accessKey;
    /** 私密密钥。 */
    private String secretKey;
    /** 保存文档的 bucket 名称。 */
    private String bucketName;
    /** 是否使用 HTTPS。 */
    private boolean secure;
}
