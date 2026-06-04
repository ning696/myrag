package com.zc.iflyzcragback.controller;

import com.zc.iflyzcragback.common.Result;
import com.zc.iflyzcragback.dto.LoginRequest;
import com.zc.iflyzcragback.dto.LoginResponse;
import com.zc.iflyzcragback.dto.UserVO;
import com.zc.iflyzcragback.security.SecurityUtils;
import com.zc.iflyzcragback.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
/**
 * 认证接口控制器。
 *
 * <p>负责登录和获取当前用户信息。登录成功后返回 JWT，前端后续请求需要把 JWT
 * 放到 Authorization 请求头中。</p>
 */
public class AuthController {

    private final AuthService authService;

    /**
     * 用户登录接口。
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return Result.success(authService.login(req));
    }

    /**
     * 获取当前登录用户资料。
     */
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.success(authService.me(SecurityUtils.getCurrentUserId()));
    }
}
