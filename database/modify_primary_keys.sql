-- 第二步：修改主键为联合主键
-- 仅在数据更新完成后执行此脚本
-- 执行时间: 2026-02-02

-- 检查是否还有 NULL 值
SELECT
    'emby_library' AS table_name,
    COUNT(*) AS null_count
FROM emby_library
WHERE emby_config_id IS NULL
UNION ALL
SELECT
    'emby_item' AS table_name,
    COUNT(*) AS null_count
FROM emby_item
WHERE emby_config_id IS NULL
UNION ALL
SELECT
    'emby_genre' AS table_name,
    COUNT(*) AS null_count
FROM emby_genre
WHERE emby_config_id IS NULL
UNION ALL
SELECT
    'emby_tag' AS table_name,
    COUNT(*) AS null_count
FROM emby_tag
WHERE emby_config_id IS NULL
UNION ALL
SELECT
    'emby_studio' AS table_name,
    COUNT(*) AS null_count
FROM emby_studio
WHERE emby_config_id IS NULL;

-- 如果上面的查询显示所有表的 null_count 都是 0，则继续执行下面的语句

-- emby_library: 改为联合主键 (id, emby_config_id)
ALTER TABLE `emby_library` DROP PRIMARY KEY;
ALTER TABLE `emby_library` MODIFY COLUMN `emby_config_id` BIGINT NOT NULL COMMENT 'Emby配置ID';
ALTER TABLE `emby_library` ADD PRIMARY KEY (`id`, `emby_config_id`);
SELECT 'emby_library 主键修改完成' AS status;

-- emby_item: 改为联合主键 (id, emby_config_id)
ALTER TABLE `emby_item` DROP PRIMARY KEY;
ALTER TABLE `emby_item` MODIFY COLUMN `emby_config_id` BIGINT NOT NULL COMMENT 'Emby配置ID';
ALTER TABLE `emby_item` ADD PRIMARY KEY (`id`, `emby_config_id`);
SELECT 'emby_item 主键修改完成' AS status;

-- emby_genre: 改为联合主键 (id, emby_config_id)
ALTER TABLE `emby_genre` DROP PRIMARY KEY;
ALTER TABLE `emby_genre` MODIFY COLUMN `emby_config_id` BIGINT NOT NULL COMMENT 'Emby配置ID';
ALTER TABLE `emby_genre` ADD PRIMARY KEY (`id`, `emby_config_id`);
SELECT 'emby_genre 主键修改完成' AS status;

-- emby_tag: 改为联合主键 (id, emby_config_id)
ALTER TABLE `emby_tag` DROP PRIMARY KEY;
ALTER TABLE `emby_tag` MODIFY COLUMN `emby_config_id` BIGINT NOT NULL COMMENT 'Emby配置ID';
ALTER TABLE `emby_tag` ADD PRIMARY KEY (`id`, `emby_config_id`);
SELECT 'emby_tag 主键修改完成' AS status;

-- emby_studio: 改为联合主键 (id, emby_config_id)
ALTER TABLE `emby_studio` DROP PRIMARY KEY;
ALTER TABLE `emby_studio` MODIFY COLUMN `emby_config_id` BIGINT NOT NULL COMMENT 'Emby配置ID';
ALTER TABLE `emby_studio` ADD PRIMARY KEY (`id`, `emby_config_id`);
SELECT 'emby_studio 主键修改完成' AS status;

SELECT '所有主键修改完成！' AS final_status;
