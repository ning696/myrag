package com.zc.iflyzcragback.security;

import com.zc.iflyzcragback.config.JwtProperties;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
/**
 * JWT 认证过滤器。
 *
 * <p>每个请求进入 Controller 前都会经过这里。它从 Authorization 请求头中取出 JWT，
 * 解析成功后把用户身份放进 Spring Security 的 SecurityContext。</p>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;
    private final JwtProperties props;

    @Override
    /**
     * 执行请求认证。
     */
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(props.getHeader());
        if (header == null || !header.startsWith(props.getPrefix())) {
            // 没带 token 的请求继续往后走，最终是否允许访问由 SecurityConfig 决定。
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(props.getPrefix().length()).trim();
        try {
            // subject 中保存 userId，解析后再查数据库，确保用户仍然存在且状态有效。
            Claims claims = jwtService.parse(token);
            Long userId = Long.valueOf(claims.getSubject());
            UserDetails user = userDetailsService.loadById(userId);

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (Exception ex) {
            log.warn("JWT 解析失败: {}", ex.getMessage());
        }

        chain.doFilter(request, response);
    }
}
