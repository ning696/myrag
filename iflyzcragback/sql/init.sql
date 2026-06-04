-- =====================================================================
-- myrag MySQL 初始化脚本
-- 用途：首次部署时创建数据库 / 8 张业务表 / 默认管理员账号
-- 执行：mysql -u root -p < iflyzcragback/sql/init.sql
-- 幂等：全部使用 IF NOT EXISTS / WHERE NOT EXISTS，可重复执行不会丢数据
-- 版本：MySQL 8.0+ ，引擎 InnoDB ，字符集 utf8mb4
-- 对齐：与 application.yml 全局配置 mybatis-plus.global-config.db-config
--       (logic-delete-field=deleted, logic-delete-value=1, logic-not-delete-value=0)
-- 表清单（按 FK 依赖顺序建表）：
--   1.  users              用户与认证
--   2.  documents          文档元信息（→ users）
--   2.1 document_chunks    分块表，BM25 全文检索 + 上下文扩展（→ documents）
--   3.  chat_sessions      会话（→ users）
--   4.  chat_messages      消息（→ chat_sessions）
--   4.1 message_feedback   消息点赞/点踩（→ chat_messages, users）
--   5.  skill_states       Skill 状态机（→ chat_sessions）
--   6.  plugins_config     插件配置（无外键）
-- =====================================================================

CREATE DATABASE IF NOT EXISTS `myrag`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `myrag`;

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. users 用户表
--    - 认证 / 鉴权主表，密码以 BCrypt 存储，永不明文
--    - 登录失败计数与账号锁定走 Redis（见 application.yml: data.redis），不在本表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `users` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT                       COMMENT '用户ID',
    `username`   VARCHAR(50)  NOT NULL                                      COMMENT '用户名（唯一）',
    `email`      VARCHAR(100) NOT NULL                                      COMMENT '邮箱（唯一）',
    `password`   VARCHAR(100) NOT NULL                                      COMMENT '密码（BCrypt 加密）',
    `nickname`   VARCHAR(50)           DEFAULT NULL                         COMMENT '昵称',
    `avatar`     VARCHAR(255)          DEFAULT NULL                         COMMENT '头像URL',
    `current_location` VARCHAR(255)    DEFAULT NULL                         COMMENT '当前所在位置（天气插件默认位置，如城市/区县/经纬度）',
    `role`       VARCHAR(20)  NOT NULL DEFAULT 'USER'                       COMMENT '角色：USER / ADMIN',
    `status`     VARCHAR(20)  NOT NULL DEFAULT 'active'                     COMMENT '状态：active / locked / disabled',
    `created_at` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP            COMMENT '注册时间',
    `updated_at` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                              ON UPDATE CURRENT_TIMESTAMP                   COMMENT '更新时间',
    `deleted`    TINYINT      NOT NULL DEFAULT 0                            COMMENT '逻辑删除：0=未删, 1=已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email`    (`email`),
    KEY `idx_role`    (`role`),
    KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 老库兼容：users 已存在时，重复执行 init.sql 也能补齐 current_location 字段。
SET @users_current_location_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'current_location'
);
SET @users_current_location_ddl := IF(
    @users_current_location_exists = 0,
    'ALTER TABLE `users` ADD COLUMN `current_location` VARCHAR(255) DEFAULT NULL COMMENT ''当前所在位置（天气插件默认位置，如城市/区县/经纬度）'' AFTER `avatar`',
    'SELECT 1'
);
PREPARE users_current_location_stmt FROM @users_current_location_ddl;
EXECUTE users_current_location_stmt;
DEALLOCATE PREPARE users_current_location_stmt;

-- ---------------------------------------------------------------------
-- 2. documents 文档元信息表
--    - 仅存元数据；向量 / chunk 文本存于 Milvus，由 vector_store_id 关联
--    - user_id 外键实现文档级数据隔离
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `documents` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT                   COMMENT '文档ID',
    `user_id`          BIGINT       NOT NULL                                  COMMENT '所属用户ID',
    `filename`         VARCHAR(255) NOT NULL                                  COMMENT '文件名',
    `file_type`        VARCHAR(20)  NOT NULL                                  COMMENT '文件类型：PDF / TXT / MD',
    `file_size`        BIGINT       NOT NULL                                  COMMENT '文件大小（字节）',
    `upload_time`      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP        COMMENT '上传时间',
    `chunk_count`      INT          NOT NULL DEFAULT 0                        COMMENT '分块数量',
    `status`           VARCHAR(20)  NOT NULL DEFAULT 'processing'             COMMENT '处理状态：processing / completed / failed',
    `vector_store_id`  VARCHAR(100)          DEFAULT NULL                     COMMENT '向量库中的文档ID（Milvus）',
    `embedding_version` VARCHAR(32)          DEFAULT NULL                     COMMENT '索引时使用的 embedding 版本（如 v2-1536），切换模型后批量重建用',
    `error_message`    TEXT                  DEFAULT NULL                     COMMENT '处理失败错误信息',
    `created_at`       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP        COMMENT '创建时间',
    `updated_at`       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                                    ON UPDATE CURRENT_TIMESTAMP               COMMENT '更新时间',
    `deleted`          TINYINT      NOT NULL DEFAULT 0                        COMMENT '逻辑删除：0=未删, 1=已删',
    PRIMARY KEY (`id`),
    KEY `idx_user_id`           (`user_id`),
    KEY `idx_status`            (`status`),
    KEY `idx_upload_time`       (`upload_time`),
    KEY `idx_embedding_version` (`embedding_version`),
    KEY `idx_deleted`           (`deleted`),
    CONSTRAINT `fk_documents_user`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档元信息表';

-- ---------------------------------------------------------------------
-- 2.1 document_chunks 文档分块表
--    - 用途：BM25 全文检索（混合检索的关键字半边）+ 上下文窗口扩展
--    - 与 Milvus 共生：vector_id 是 Milvus 端主键，便于跨库定位
--    - user_id 冗余一份，避免 BM25 查询时还要 JOIN documents 做隔离过滤
--    - FULLTEXT 用 ngram parser 适配中文（默认 token size = 2）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `document_chunks` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT                    COMMENT '分块ID',
    `document_id`    BIGINT       NOT NULL                                   COMMENT '所属文档ID',
    `user_id`        BIGINT       NOT NULL                                   COMMENT '所属用户ID（冗余，加速 BM25 隔离过滤）',
    `chunk_index`    INT          NOT NULL                                   COMMENT '在文档内的顺序，从 0 起',
    `content`        TEXT         NOT NULL                                   COMMENT 'chunk 原文',
    `title`          VARCHAR(255)          DEFAULT NULL                      COMMENT '所属章节标题（用于 prompt 引用展示）',
    `keywords`       VARCHAR(500)          DEFAULT NULL                      COMMENT '关键词（TF-IDF/KeyBERT，逗号分隔）',
    `summary`        VARCHAR(500)          DEFAULT NULL                      COMMENT '可选 1 句话摘要（多向量检索用）',
    `vector_id`      VARCHAR(100)          DEFAULT NULL                      COMMENT 'Milvus 端主键，用于跨库定位',
    `created_at`     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP         COMMENT '创建时间',
    `deleted`        TINYINT      NOT NULL DEFAULT 0                         COMMENT '逻辑删除：0=未删, 1=已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_doc_chunk` (`document_id`, `chunk_index`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_deleted` (`deleted`),
    FULLTEXT KEY `ftx_content` (`content`) WITH PARSER ngram,
    CONSTRAINT `fk_chunks_document`
        FOREIGN KEY (`document_id`) REFERENCES `documents` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档分块表（BM25 全文检索 / 上下文扩展）';

-- ---------------------------------------------------------------------
-- 3. chat_sessions 会话表
--    - session_id 用 UUID（应用层生成）作主键
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `chat_sessions` (
    `session_id` VARCHAR(64)  NOT NULL                                      COMMENT '会话ID（UUID）',
    `user_id`    BIGINT       NOT NULL                                      COMMENT '所属用户ID',
    `title`      VARCHAR(255) NOT NULL DEFAULT '新对话'                     COMMENT '会话标题',
    `created_at` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP            COMMENT '创建时间',
    `updated_at` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                              ON UPDATE CURRENT_TIMESTAMP                   COMMENT '最后更新时间',
    `status`     VARCHAR(20)  NOT NULL DEFAULT 'active'                     COMMENT '会话状态：active / completed / archived',
    `deleted`    TINYINT      NOT NULL DEFAULT 0                            COMMENT '逻辑删除：0=未删, 1=已删',
    PRIMARY KEY (`session_id`),
    KEY `idx_user_id`    (`user_id`),
    KEY `idx_created_at` (`created_at`),
    KEY `idx_deleted`    (`deleted`),
    CONSTRAINT `fk_sessions_user`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话记录表';

-- ---------------------------------------------------------------------
-- 4. chat_messages 消息表
--    - context / source_documents 用 JSON 字符串存（TEXT），便于检索溯源
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `chat_messages` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT                 COMMENT '消息ID',
    `session_id`       VARCHAR(64)  NOT NULL                                COMMENT '所属会话ID',
    `role`             VARCHAR(20)  NOT NULL                                COMMENT '角色：user / assistant / system',
    `content`          TEXT         NOT NULL                                COMMENT '消息内容',
    `context`          TEXT                  DEFAULT NULL                   COMMENT '检索到的上下文（JSON 数组）',
    `source_documents` TEXT                  DEFAULT NULL                   COMMENT '来源文档列表（JSON 数组）',
    `plugin_used`      VARCHAR(255)          DEFAULT NULL                   COMMENT '使用的插件名称',
    `skill_used`       VARCHAR(255)          DEFAULT NULL                   COMMENT '使用的 Skill 名称',
    `tokens_used`      INT          NOT NULL DEFAULT 0                      COMMENT '消耗 token 数',
    `response_time`    INT          NOT NULL DEFAULT 0                      COMMENT '响应时间（毫秒）',
    `confidence`       DOUBLE                DEFAULT NULL                   COMMENT '检索 topScore，对应 ChatResponse.confidence；前端低置信提示用',
    `answer_mode`      VARCHAR(40)           DEFAULT NULL                   COMMENT '回答模式：CHAT / RAG_ANSWER / NO_KB_HIT / REALTIME_UNAVAILABLE',
    `created_at`       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP      COMMENT '创建时间',
    `deleted`          TINYINT      NOT NULL DEFAULT 0                      COMMENT '逻辑删除：0=未删, 1=已删',
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_created_at` (`created_at`),
    KEY `idx_deleted`    (`deleted`),
    CONSTRAINT `fk_messages_session`
        FOREIGN KEY (`session_id`) REFERENCES `chat_sessions` (`session_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息记录表';

-- 老库兼容：chat_messages 已存在时补齐 answer_mode 字段。
SET @chat_messages_answer_mode_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'chat_messages'
      AND COLUMN_NAME = 'answer_mode'
);
SET @chat_messages_answer_mode_ddl := IF(
    @chat_messages_answer_mode_exists = 0,
    'ALTER TABLE `chat_messages` ADD COLUMN `answer_mode` VARCHAR(40) DEFAULT NULL COMMENT ''回答模式：CHAT / RAG_ANSWER / NO_KB_HIT / REALTIME_UNAVAILABLE'' AFTER `confidence`',
    'SELECT 1'
);
PREPARE chat_messages_answer_mode_stmt FROM @chat_messages_answer_mode_ddl;
EXECUTE chat_messages_answer_mode_stmt;
DEALLOCATE PREPARE chat_messages_answer_mode_stmt;

-- ---------------------------------------------------------------------
-- 4.1 message_feedback 用户消息反馈
--    - 用途：收集点赞/点踩，为 hard negatives 挖掘和效果评估提供素材
--    - 同一 (user_id, message_id) 仅允许一条记录，再次点击走 UPDATE
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `message_feedback` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT                       COMMENT '反馈ID',
    `message_id` BIGINT       NOT NULL                                      COMMENT '所属消息ID',
    `user_id`    BIGINT       NOT NULL                                      COMMENT '反馈用户ID',
    `rating`     TINYINT      NOT NULL                                      COMMENT '评分：1=赞, -1=踩',
    `reason`     VARCHAR(500)          DEFAULT NULL                         COMMENT '踩的原因（可选）',
    `created_at` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP             COMMENT '创建时间',
    `updated_at` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                              ON UPDATE CURRENT_TIMESTAMP                    COMMENT '更新时间',
    `deleted`    TINYINT      NOT NULL DEFAULT 0                             COMMENT '逻辑删除：0=未删, 1=已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_message` (`user_id`, `message_id`),
    KEY `idx_message_id` (`message_id`),
    KEY `idx_rating`     (`rating`),
    KEY `idx_deleted`    (`deleted`),
    CONSTRAINT `fk_feedback_message`
        FOREIGN KEY (`message_id`) REFERENCES `chat_messages` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_feedback_user`
        FOREIGN KEY (`user_id`)    REFERENCES `users`         (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户消息反馈表（hard negatives 来源）';

-- ---------------------------------------------------------------------
-- 5. skill_states Skill 状态表
--    - 多轮任务状态机的持久化（按 session 维度隔离）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `skill_states` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT                    COMMENT '状态ID',
    `session_id`    VARCHAR(64)  NOT NULL                                   COMMENT '所属会话ID',
    `skill_name`    VARCHAR(100) NOT NULL                                   COMMENT 'Skill 名称',
    `current_step`  VARCHAR(100)          DEFAULT NULL                      COMMENT '当前步骤',
    `state_data`    TEXT                  DEFAULT NULL                      COMMENT '状态数据（JSON）',
    `is_completed`  TINYINT      NOT NULL DEFAULT 0                         COMMENT '是否完成：0=否, 1=是',
    `created_at`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP         COMMENT '创建时间',
    `updated_at`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                                 ON UPDATE CURRENT_TIMESTAMP                COMMENT '更新时间',
    `deleted`       TINYINT      NOT NULL DEFAULT 0                         COMMENT '逻辑删除：0=未删, 1=已删',
    PRIMARY KEY (`id`),
    KEY `idx_session_id`   (`session_id`),
    KEY `idx_is_completed` (`is_completed`),
    KEY `idx_deleted`      (`deleted`),
    CONSTRAINT `fk_skill_states_session`
        FOREIGN KEY (`session_id`) REFERENCES `chat_sessions` (`session_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Skill 状态表';

-- ---------------------------------------------------------------------
-- 6. plugins_config 插件配置表
--    - 全局配置（不区分用户），仅 ADMIN 可写
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `plugins_config` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT                     COMMENT '配置ID',
    `plugin_name`  VARCHAR(100) NOT NULL                                    COMMENT '插件名称（唯一）',
    `enabled`      TINYINT      NOT NULL DEFAULT 1                          COMMENT '是否启用：0=禁用, 1=启用',
    `config_json`  TEXT                  DEFAULT NULL                       COMMENT '插件参数（JSON）',
    `description`  VARCHAR(500)          DEFAULT NULL                       COMMENT '插件描述',
    `hook_type`    VARCHAR(20)  NOT NULL DEFAULT 'both'                     COMMENT '钩子类型：before / after / both',
    `priority`     INT          NOT NULL DEFAULT 0                          COMMENT '优先级（越大越先执行）',
    `created_at`   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP          COMMENT '创建时间',
    `updated_at`   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                                ON UPDATE CURRENT_TIMESTAMP                 COMMENT '更新时间',
    `deleted`      TINYINT      NOT NULL DEFAULT 0                          COMMENT '逻辑删除：0=未删, 1=已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plugin_name` (`plugin_name`),
    KEY `idx_enabled`  (`enabled`),
    KEY `idx_priority` (`priority`),
    KEY `idx_deleted`  (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='插件配置表';

-- =====================================================================
-- 初始化数据
-- =====================================================================

-- 默认管理员账号
--   username : admin
--   password : admin123  ← 明文，仅供首次登录，**生产环境必须立即修改**
--   hash     : 由 Spring Security BCryptPasswordEncoder(strength=10) 生成
--              已用 matches() 验证可被同算法校验通过
INSERT INTO `users` (`username`, `email`, `password`, `nickname`, `role`, `status`)
SELECT 'admin',
       'admin@myrag.local',
       '$2a$10$YKoXIll1lfPYNWTgB/ltD.J2CZR/7GujzHGz7WlosXcPAZfqkPTu.',
       '系统管理员',
       'ADMIN',
       'active'
WHERE NOT EXISTS (SELECT 1 FROM `users` WHERE `username` = 'admin');
