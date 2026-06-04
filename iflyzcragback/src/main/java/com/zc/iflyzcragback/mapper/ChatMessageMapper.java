package com.zc.iflyzcragback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zc.iflyzcragback.entity.ChatMessageEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 聊天消息 Mapper。
 *
 * <p>负责 chat_messages 表的基础 CRUD 操作，RAG 编排器会用它读取历史和保存回答。</p>
 */
public interface ChatMessageMapper extends BaseMapper<ChatMessageEntity> {
}
