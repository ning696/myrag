package com.zc.iflyzcragback.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zc.iflyzcragback.entity.UserEntity;
import com.zc.iflyzcragback.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
/**
 * Spring Security 用户加载服务。
 *
 * <p>登录时按用户名加载用户；JWT 过滤器认证时按 userId 加载用户。
 * 返回的 CurrentUser 会进入 SecurityContext，成为本次请求的身份。</p>
 */
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;

    @Override
    /**
     * 按用户名加载用户，主要用于用户名密码登录流程。
     */
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, username));
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        return new CurrentUser(user);
    }

    /**
     * 按用户 ID 加载用户，主要用于 JWT 已经解析出 subject 后恢复登录态。
     */
    public UserDetails loadById(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + userId);
        }
        return new CurrentUser(user);
    }
}
