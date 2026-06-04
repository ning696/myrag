package com.zc.iflyzcragback.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文工具类。
 *
 * <p>业务代码不需要直接操作 Spring Security 的复杂对象，只要通过这里取当前用户信息即可。</p>
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    /**
     * 获取当前登录用户 ID。
     */
    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CurrentUser u)) {
            throw new IllegalStateException("未认证");
        }
        return u.getUserId();
    }

    /**
     * 获取当前登录用户名。
     */
    public static String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CurrentUser u)) {
            throw new IllegalStateException("未认证");
        }
        return u.getUsername();
    }

    /**
     * 获取当前登录用户角色。
     */
    public static String getCurrentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CurrentUser u)) {
            throw new IllegalStateException("未认证");
        }
        return u.getRole();
    }
}
