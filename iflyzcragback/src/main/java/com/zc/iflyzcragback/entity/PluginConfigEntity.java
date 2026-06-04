package com.zc.iflyzcragback.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("plugins_config")
/**
 * 插件配置实体。
 *
 * <p>插件本身由 Spring Bean 提供，这张表只控制启用状态、执行顺序和非敏感参数。</p>
 */
public class PluginConfigEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String pluginName;
    private Integer enabled;
    private String configJson;
    private String description;
    private String hookType;
    private Integer priority;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    public boolean isEnabled() {
        return enabled != null && enabled == 1;
    }
}
