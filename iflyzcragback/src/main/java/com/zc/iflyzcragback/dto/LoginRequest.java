package com.zc.iflyzcragback.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
/**
 * 登录请求 DTO。
 *
 * <p>DTO 是前端传给后端的数据结构。这里用于接收用户名、密码和是否记住登录。</p>
 */
public class LoginRequest {
    @NotBlank(message = "用户名不能为空")
    /** 登录用户名。 */
    private String username;

    @NotBlank(message = "密码不能为空")
    /** 登录密码。 */
    private String password;

    /** 是否延长 token 有效期。 */
    private Boolean rememberMe = false;
}
