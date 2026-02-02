-- 智能修改主键脚本 - 逐个表处理
-- 自动跳过没有主键的表
-- 执行时间: 2026-02-02

-- ============================================
-- emby_library 表
-- ============================================
SELECT '处理 emby_library 表...' AS status;

-- 尝试删除主键（如果存在）
SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.KEY_COLUMN_USAGE
            WHERE TABLE_SCHEMA = 'gd_upload_manager'
              AND TABLE_NAME = 'emby_library'
              AND CONSTRAINT_NAME = 'PRIMARY'
        ),
        'ALTER TABLE `emby_library` DROP PRIMARY KEY',
        'SELECT "emby_library 表没有主键，跳过删除" AS info'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 修改字段为 NOT NULL
ALTER TABLE `emby_library` MODIFY COLUMN `emby_config_id` BIGINT NOT NULL COMMENT 'Emby配置ID';

-- 添加联合主键
ALTER TABLE `emby_library` ADD PRIMARY KEY (`id`, `emby_config_id`);
SELECT 'emby_library 完成' AS status;

-- ============================================
-- emby_item 表
-- ============================================
SELECT '处理 emby_item 表...' AS status;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.KEY_COLUMN_USAGE
            WHERE TABLE_SCHEMA = 'gd_upload_manager'
              AND TABLE_NAME = 'emby_item'
              AND CONSTRAINT_NAME = 'PRIMARY'
        ),
        'ALTER TABLE `emby_item` DROP PRIMARY KEY',
        'SELECT "emby_item 表没有主键，跳过删除" AS info'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE `emby_item` MODIFY COLUMN `emby_config_id` BIGINT NOT NULL COMMENT 'Emby配置ID';
ALTER TABLE `emby_item` ADD PRIMARY KEY (`id`, `emby_config_id`);
SELECT 'emby_item 完成' AS status;

-- ============================================
-- emby_genre 表
-- ============================================
SELECT '处理 emby_genre 表...' AS status;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.KEY_COLUMN_USAGE
            WHERE TABLE_SCHEMA = 'gd_upload_manager'
              AND TABLE_NAME = 'emby_genre'
              AND CONSTRAINT_NAME = 'PRIMARY'
        ),
        'ALTER TABLE `emby_genre` DROP PRIMARY KEY',
        'SELECT "emby_genre 表没有主键，跳过删除" AS info'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE `emby_genre` MODIFY COLUMN `emby_config_id` BIGINT NOT NULL COMMENT 'Emby配置ID';
ALTER TABLE `emby_genre` ADD PRIMARY KEY (`id`, `emby_config_id`);
SELECT 'emby_genre 完成' AS status;

-- ============================================
-- emby_tag 表
-- ============================================
SELECT '处理 emby_tag 表...' AS status;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.KEY_COLUMN_USAGE
            WHERE TABLE_SCHEMA = 'gd_upload_manager'
              AND TABLE_NAME = 'emby_tag'
              AND CONSTRAINT_NAME = 'PRIMARY'
        ),
        'ALTER TABLE `emby_tag` DROP PRIMARY KEY',
        'SELECT "emby_tag 表没有主键，跳过删除" AS info'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE `emby_tag` MODIFY COLUMN `emby_config_id` BIGINT NOT NULL COMMENT 'Emby配置ID';
ALTER TABLE `emby_tag` ADD PRIMARY KEY (`id`, `emby_config_id`);
SELECT 'emby_tag 完成' AS status;

-- ============================================
-- emby_studio 表
-- ============================================
SELECT '处理 emby_studio 表...' AS status;

SET @sql = (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.KEY_COLUMN_USAGE
            WHERE TABLE_SCHEMA = 'gd_upload_manager'
              AND TABLE_NAME = 'emby_studio'
              AND CONSTRAINT_NAME = 'PRIMARY'
        ),
        'ALTER TABLE `emby_studio` DROP PRIMARY KEY',
        'SELECT "emby_studio 表没有主键，跳过删除" AS info'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE `emby_studio` MODIFY COLUMN `emby_config_id` BIGINT NOT NULL COMMENT 'Emby配置ID';
ALTER TABLE `emby_studio` ADD PRIMARY KEY (`id`, `emby_config_id`);
SELECT 'emby_studio 完成' AS status;

-- ============================================
-- 完成
-- ============================================
SELECT '所有表的主键修改完成！' AS final_status;

-- 验证结果
SELECT
    TABLE_NAME,
    COLUMN_NAME,
    ORDINAL_POSITION
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = 'gd_upload_manager'
  AND TABLE_NAME IN ('emby_library', 'emby_item', 'emby_genre', 'emby_tag', 'emby_studio')
  AND CONSTRAINT_NAME = 'PRIMARY'
ORDER BY TABLE_NAME, ORDINAL_POSITION;
