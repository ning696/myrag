package com.zc.iflyzcragback.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "cors")
/**
 * CORS 配置属性。
 *
 * <p>对应 application.yml 中的 cors.* 配置，用于控制前端跨域访问。</p>
 */
public class CorsProperties {
    /** 允许访问后端的前端地址，多个地址用逗号分隔。 */
    private String allowedOrigins;
    /** 允许的 HTTP 方法，例如 GET,POST,PUT,DELETE。 */
    private String allowedMethods;
    /** 是否允许浏览器携带 cookie/认证信息。 */
    private boolean allowCredentials = true;
    /** 浏览器预检请求缓存时间，单位秒。 */
    private long maxAge = 3600L;
}
