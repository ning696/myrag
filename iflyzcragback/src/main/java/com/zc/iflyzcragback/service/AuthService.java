package com.zc.iflyzcragback.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zc.iflyzcragback.common.BizException;
import com.zc.iflyzcragback.dto.LoginRequest;
import com.zc.iflyzcragback.dto.LoginResponse;
import com.zc.iflyzcragback.dto.UserVO;
import com.zc.iflyzcragback.entity.UserEntity;
import com.zc.iflyzcragback.mapper.UserMapper;
import com.zc.iflyzcragback.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String LOGIN_FAIL_KEY = "login:fail:";
    private static final int MAX_FAIL_COUNT = 5;
    private static final long LOCK_MINUTES = 15;

    public LoginResponse login(LoginRequest req) {
        String failKey = LOGIN_FAIL_KEY + req.getUsername();
        Integer failCount = (Integer) redisTemplate.opsForValue().get(failKey);
        if (failCount != null && failCount >= MAX_FAIL_COUNT) {
            throw new BizException("登录失败次数过多，账号已锁定 " + LOCK_MINUTES + " 分钟");
        }

        UserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, req.getUsername()));
        if (user == null) {
            incrFailCount(failKey);
            throw new BizException("用户名或密码错误");
        }

        if (!"active".equalsIgnoreCase(user.getStatus())) {
            throw new BizException("账号已被禁用");
        }

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            incrFailCount(failKey);
            throw new BizException("用户名或密码错误");
        }

        // 登录成功，清除失败记录
        redisTemplate.delete(failKey);

        String token = jwtService.generate(user.getId(), user.getUsername(),
                user.getRole(), req.getRememberMe());

        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        log.info("用户登录成功: userId={}, username={}", user.getId(), user.getUsername());
        return new LoginResponse(token, userVO);
    }

    private void incrFailCount(String key) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, LOCK_MINUTES, TimeUnit.MINUTES);
        }
    }

    public UserVO me(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }
}
