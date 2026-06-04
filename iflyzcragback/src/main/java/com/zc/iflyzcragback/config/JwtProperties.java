package com.zc.iflyzcragback.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
/**
 * JWT 配置属性。
 *
 * <p>对应 application.yml 中的 jwt.*，控制 token 签名密钥、有效期和请求头格式。</p>
 */
public class JwtProperties {
    /** JWT 签名密钥，生产环境必须使用足够长且不可泄露的随机字符串。 */
    private String secret;
    /** 普通登录 token 有效期，单位毫秒。 */
    private long expiration;
    /** 记住我登录 token 有效期，单位毫秒。 */
    private long rememberMeExpiration;
    /** 前端携带 token 的请求头名称，通常是 Authorization。 */
    private String header;
    /** token 前缀，通常是 Bearer。 */
    private String prefix;
}
