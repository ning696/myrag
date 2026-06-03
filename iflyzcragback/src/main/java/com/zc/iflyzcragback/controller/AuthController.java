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
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return Result.success(authService.login(req));
    }

    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.success(authService.me(SecurityUtils.getCurrentUserId()));
    }
}
