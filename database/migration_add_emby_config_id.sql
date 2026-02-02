-- 添加 emby_config_id 字段以支持多个 Emby 服务器
-- 执行时间: 2026-02-02

-- 1. 为 emby_library 表添加 emby_config_id 字段
ALTER TABLE `emby_library`
ADD COLUMN `emby_config_id` BIGINT NULL COMMENT 'Emby配置ID' AFTER `server_id`,
ADD INDEX `idx_emby_config_id` (`emby_config_id`);

-- 2. 为 emby_item 表添加 emby_config_id 字段
ALTER TABLE `emby_item`
ADD COLUMN `emby_config_id` BIGINT NULL COMMENT 'Emby配置ID' AFTER `server_id`,
ADD INDEX `idx_emby_config_id` (`emby_config_id`),
ADD INDEX `idx_config_parent` (`emby_config_id`, `parent_id`);

-- 3. 为 emby_genre 表添加 emby_config_id 字段
ALTER TABLE `emby_genre`
ADD COLUMN `emby_config_id` BIGINT NULL COMMENT 'Emby配置ID' AFTER `server_id`,
ADD INDEX `idx_emby_config_id` (`emby_config_id`);

-- 4. 为 emby_tag 表添加 emby_config_id 字段
ALTER TABLE `emby_tag`
ADD COLUMN `emby_config_id` BIGINT NULL COMMENT 'Emby配置ID' AFTER `server_id`,
ADD INDEX `idx_emby_config_id` (`emby_config_id`);

-- 5. 为 emby_studio 表添加 emby_config_id 字段
ALTER TABLE `emby_studio`
ADD COLUMN `emby_config_id` BIGINT NULL COMMENT 'Emby配置ID' AFTER `server_id`,
ADD INDEX `idx_emby_config_id` (`emby_config_id`);

-- 6. 为现有数据设置默认的 emby_config_id
-- 假设第一个 Emby 配置的 ID 为 1，将所有现有数据关联到该配置
-- 如果您的配置 ID 不是 1，请修改下面的 SQL 语句

-- 获取默认配置的 ID（is_default = 1 的配置）
SET @default_config_id = (SELECT id FROM emby_config WHERE is_default = 1 LIMIT 1);

-- 如果没有默认配置，使用第一个配置
SET @default_config_id = IFNULL(@default_config_id, (SELECT id FROM emby_config ORDER BY id LIMIT 1));

-- 更新现有数据
UPDATE `emby_library` SET `emby_config_id` = @default_config_id WHERE `emby_config_id` IS NULL;
UPDATE `emby_item` SET `emby_config_id` = @default_config_id WHERE `emby_config_id` IS NULL;
UPDATE `emby_genre` SET `emby_config_id` = @default_config_id WHERE `emby_config_id` IS NULL;
UPDATE `emby_tag` SET `emby_config_id` = @default_config_id WHERE `emby_config_id` IS NULL;
UPDATE `emby_studio` SET `emby_config_id` = @default_config_id WHERE `emby_config_id` IS NULL;

-- 7. 修改主键，支持多个 Emby 服务器的相同 ID
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

-- 说明：
-- 1. emby_config_id 关联 emby_config 表的 id 字段
-- 2. 联合主键确保同一个 Emby 服务器的 ID 不会重复
-- 3. 不同 Emby 服务器可以有相同的 ID（如都有 ID 为 "258" 的媒体库）
-- 4. 查询时需要同时指定 emby_config_id 和其他条件
-- 5. emby_config_id 设置为 NOT NULL，必须指定配置ID
