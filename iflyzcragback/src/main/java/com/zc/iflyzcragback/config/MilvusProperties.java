package com.zc.iflyzcragback.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "milvus")
public class MilvusProperties {
    private String host;
    private int port = 19530;
    private String username;
    private String password;
    private String collectionName;
    private int dimension;
}
