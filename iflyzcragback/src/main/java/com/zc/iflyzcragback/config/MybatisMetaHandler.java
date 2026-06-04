package com.zc.iflyzcragback.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
/**
 * MyBatis-Plus 自动填充处理器。
 *
 * <p>实体字段标注 FieldFill 后，插入或更新数据库时会自动填充 createdAt、updatedAt 等时间字段。</p>
 */
public class MybatisMetaHandler implements MetaObjectHandler {

    @Override
    /**
     * 新增数据时填充创建时间、更新时间和上传时间。
     */
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "uploadTime", LocalDateTime.class, now);
    }

    @Override
    /**
     * 更新数据时刷新 updatedAt。
     */
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
