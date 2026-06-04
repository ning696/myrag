package com.zc.iflyzcragback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zc.iflyzcragback.entity.DocumentEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 文档表 Mapper。
 *
 * <p>负责 documents 表的基础 CRUD 操作。</p>
 */
public interface DocumentMapper extends BaseMapper<DocumentEntity> {
}
