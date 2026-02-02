-- 修复 emby_config_id 为 NULL 的数据
-- 用于已经执行了 ALTER TABLE 添加字段，但还没有设置值的情况
-- 执行时间: 2026-02-02

-- 获取默认配置的 ID（is_default = 1 的配置）
SET @default_config_id = (SELECT id FROM emby_config WHERE is_default = 1 LIMIT 1);

-- 如果没有默认配置，使用第一个配置
SET @default_config_id = IFNULL(@default_config_id, (SELECT id FROM emby_config ORDER BY id LIMIT 1));

-- 显示将要使用的配置 ID
SELECT CONCAT('将使用配置 ID: ', @default_config_id) AS info;

-- 更新现有数据
UPDATE `emby_library` SET `emby_config_id` = @default_config_id WHERE `emby_config_id` IS NULL;
UPDATE `emby_item` SET `emby_config_id` = @default_config_id WHERE `emby_config_id` IS NULL;
UPDATE `emby_genre` SET `emby_config_id` = @default_config_id WHERE `emby_config_id` IS NULL;
UPDATE `emby_tag` SET `emby_config_id` = @default_config_id WHERE `emby_config_id` IS NULL;
UPDATE `emby_studio` SET `emby_config_id` = @default_config_id WHERE `emby_config_id` IS NULL;

-- 显示更新结果
SELECT
    (SELECT COUNT(*) FROM emby_library WHERE emby_config_id IS NOT NULL) AS library_count,
    (SELECT COUNT(*) FROM emby_item WHERE emby_config_id IS NOT NULL) AS item_count,
    (SELECT COUNT(*) FROM emby_genre WHERE emby_config_id IS NOT NULL) AS genre_count,
    (SELECT COUNT(*) FROM emby_tag WHERE emby_config_id IS NOT NULL) AS tag_count,
    (SELECT COUNT(*) FROM emby_studio WHERE emby_config_id IS NOT NULL) AS studio_count;

-- 修改主键，支持多个 Emby 服务器的相同 ID
-- emby_library: 改为联合主键 (id, emby_config_id)
ALTER TABLE `emby_library` DROP PRIMARY KEY;
ALTER TABLE `emby_library` MODIFY COLUMN `emby_config_id` BIGINT NOT NULL COMMENT 'Emby配置ID';
ALTER TABLE `emby_library` ADD PRIMARY KEY (`id`, `emby_config_id`);

-- emby_item: 改为联合主键 (id, emby_config_id)
ALTER TABLE `emby_item` DROP PRIMARY KEY;
ALTER TABLE `emby_item` MODIFY COLUMN `emby_config_id` BIGINT NOT NULL COMMENT 'Emby配置ID';
ALTER TABLE `emby_item` ADD PRIMARY KEY (`id`, `emby_config_id`);

-- emby_genre: 改为联合主键 (id, emby_config_id)
ALTER TABLE `emby_genre` DROP PRIMARY KEY;
ALTER TABLE `emby_genre` MODIFY COLUMN `emby_config_id` BIGINT NOT NULL COMMENT 'Emby配置ID';
ALTER TABLE `emby_genre` ADD PRIMARY KEY (`id`, `emby_config_id`);

-- emby_tag: 改为联合主键 (id, emby_config_id)
ALTER TABLE `emby_tag` DROP PRIMARY KEY;
ALTER TABLE `emby_tag` MODIFY COLUMN `emby_config_id` BIGINT NOT NULL COMMENT 'Emby配置ID';
ALTER TABLE `emby_tag` ADD PRIMARY KEY (`id`, `emby_config_id`);

-- emby_studio: 改为联合主键 (id, emby_config_id)
ALTER TABLE `emby_studio` DROP PRIMARY KEY;
ALTER TABLE `emby_studio` MODIFY COLUMN `emby_config_id` BIGINT NOT NULL COMMENT 'Emby配置ID';
ALTER TABLE `emby_studio` ADD PRIMARY KEY (`id`, `emby_config_id`);

SELECT '迁移完成！' AS status;
