package com.zc.iflyzcragback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zc.iflyzcragback.entity.ChatSessionEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 聊天会话 Mapper。
 *
 * <p>负责 chat_sessions 表的基础 CRUD 操作。</p>
 */
public interface ChatSessionMapper extends BaseMapper<ChatSessionEntity> {
}
