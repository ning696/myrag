package com.zc.iflyzcragback.dto;

import lombok.Data;

@Data
/**
 * 用户展示 VO。
 *
 * <p>返回给前端的用户信息，不包含密码等敏感字段。</p>
 */
public class UserVO {
    /** 用户 ID。 */
    private Long id;
    /** 用户名。 */
    private String username;
    /** 邮箱。 */
    private String email;
    /** 昵称。 */
    private String nickname;
    /** 头像地址。 */
    private String avatar;
    /** 用户角色。 */
    private String role;
}
