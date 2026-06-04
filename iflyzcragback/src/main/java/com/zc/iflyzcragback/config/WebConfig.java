package com.zc.iflyzcragback.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

@Configuration
@RequiredArgsConstructor
/**
 * Web 通用配置。
 *
 * <p>当前主要配置 CORS。前端和后端端口不同时，浏览器会触发跨域校验，
 * 这里告诉浏览器哪些来源、方法和请求头被允许。</p>
 */
public class WebConfig {

    private final CorsProperties props;

    @Bean
    /**
     * CORS 过滤器。
     */
    public CorsFilter corsFilter() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowCredentials(props.isAllowCredentials());
        if (props.getAllowedOrigins() != null && !props.getAllowedOrigins().isBlank()) {
            cfg.setAllowedOriginPatterns(Arrays.asList(props.getAllowedOrigins().split(",")));
        } else {
            cfg.addAllowedOriginPattern("*");
        }
        if (props.getAllowedMethods() != null && !props.getAllowedMethods().isBlank()) {
            cfg.setAllowedMethods(Arrays.asList(props.getAllowedMethods().split(",")));
        } else {
            cfg.addAllowedMethod("*");
        }
        cfg.addAllowedHeader("*");
        cfg.addExposedHeader("Authorization");
        cfg.setMaxAge(props.getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return new CorsFilter(source);
    }
}
