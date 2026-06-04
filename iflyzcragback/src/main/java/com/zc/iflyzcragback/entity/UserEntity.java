package com.zc.iflyzcragback.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("users")
/**
 * 用户表实体。
 *
 * <p>对应数据库 users 表，保存登录账号、密码哈希、角色和账号状态。</p>
 */
public class UserEntity {

    @TableId(type = IdType.AUTO)
    /** 用户主键 ID。 */
    private Long id;

    /** 登录用户名。 */
    private String username;
    /** 邮箱。 */
    private String email;
    /** BCrypt 加密后的密码，不保存明文。 */
    private String password;
    /** 用户昵称。 */
    private String nickname;
    /** 头像地址。 */
    private String avatar;
    /** 用户角色，例如 admin/user。 */
    private String role;
    /** 账号状态，例如 active 表示可登录。 */
    private String status;

    @TableField(value = "created_at", fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    /** 创建时间，由 MybatisMetaHandler 自动填充。 */
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    /** 更新时间，由 MybatisMetaHandler 自动填充。 */
    private LocalDateTime updatedAt;

    @TableLogic
    /** 逻辑删除标记，1 表示已删除，0 表示未删除。 */
    private Integer deleted;
}
