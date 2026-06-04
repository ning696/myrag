package com.zc.iflyzcragback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zc.iflyzcragback.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 用户表 Mapper。
 *
 * <p>继承 BaseMapper 后，MyBatis-Plus 会自动提供 selectById、insert、updateById 等常用数据库方法。</p>
 */
public interface UserMapper extends BaseMapper<UserEntity> {
}
