package com.zc.iflyzcragback.security;

import com.zc.iflyzcragback.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
/**
 * JWT 服务。
 *
 * <p>JWT 是一种把用户身份签名后放在 token 里的机制。后端签发 token，
 * 前端保存 token 并在请求头中携带，后端再解析 token 恢复用户身份。</p>
 */
public class JwtService {

    private final JwtProperties props;
    private SecretKey key;

    @PostConstruct
    /**
     * 初始化签名密钥。HMAC-SHA 密钥太短会降低安全性，所以这里做长度校验。
     */
    void init() {
        byte[] secret = props.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("jwt.secret 至少 32 字节（256 位），当前=" + secret.length);
        }
        this.key = Keys.hmacShaKeyFor(secret);
    }

    /**
     * 生成 JWT。
     *
     * @param rememberMe 是否使用更长有效期
     */
    public String generate(Long userId, String username, String role, boolean rememberMe) {
        long now = System.currentTimeMillis();
        long ttl = rememberMe ? props.getRememberMeExpiration() : props.getExpiration();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttl))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验 JWT。签名不对或过期都会抛异常。
     */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    /**
     * 从 token 的 subject 中取出用户 ID。
     */
    public Long extractUserId(String token) {
        return Long.valueOf(parse(token).getSubject());
    }
}
