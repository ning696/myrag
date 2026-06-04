package com.zc.iflyzcragback.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * 登录响应 DTO。
 *
 * <p>登录成功后返回 JWT 和用户资料，前端后续请求需要携带 token。</p>
 */
public class LoginResponse {
    /** JWT 登录凭证。 */
    private String token;
    /** 当前用户展示信息。 */
    private UserVO user;
}
