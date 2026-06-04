package com.zc.iflyzcragback.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "milvus")
/**
 * Milvus 配置属性。
 *
 * <p>控制向量库连接地址、集合名称和向量维度。向量维度必须和 embedding 模型输出维度一致。</p>
 */
public class MilvusProperties {
    /** Milvus 服务地址。 */
    private String host;
    /** Milvus 服务端口。 */
    private int port = 19530;
    /** Milvus 用户名，可为空。 */
    private String username;
    /** Milvus 密码，可为空。 */
    private String password;
    /** 存储文档 chunk 向量的集合名称。 */
    private String collectionName;
    /** embedding 向量维度，例如 DashScope text-embedding-v2 是 1536。 */
    private int dimension;
}
