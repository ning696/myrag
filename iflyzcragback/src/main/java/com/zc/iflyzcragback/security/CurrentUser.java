package com.zc.iflyzcragback.security;

import com.zc.iflyzcragback.entity.UserEntity;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
/**
 * Spring Security 中保存的当前登录用户。
 *
 * <p>JwtAuthenticationFilter 解析 token 后，会把数据库用户包装成 CurrentUser，
 * 放入 SecurityContext。后续代码通过 SecurityUtils 就能拿到 userId、username、role。</p>
 */
public class CurrentUser implements UserDetails {

    private final Long userId;
    private final String username;
    private final String password;
    private final String role;
    private final boolean enabled;

    /**
     * 从数据库用户实体构建安全上下文用户。
     */
    public CurrentUser(UserEntity entity) {
        this.userId = entity.getId();
        this.username = entity.getUsername();
        this.password = entity.getPassword();
        this.role = entity.getRole();
        this.enabled = "active".equalsIgnoreCase(entity.getStatus());
    }

    @Override
    /**
     * 返回当前用户权限。Spring Security 约定角色权限通常以 ROLE_ 开头。
     */
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public boolean isAccountNonExpired() {
        return enabled;
    }

    @Override
    public boolean isAccountNonLocked() {
        return enabled;
    }

    @Override
    /**
     * 凭证是否未过期。当前项目暂未做密码过期策略，因此固定为 true。
     */
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
