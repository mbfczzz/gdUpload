-- 修复Emby缓存表，添加缺失的字段
-- 如果字段已存在会报错，可以忽略

-- 添加 emby_library 表的 server_id 字段
ALTER TABLE `emby_library`
ADD COLUMN `server_id` varchar(100) DEFAULT NULL COMMENT 'Emby服务器ID' AFTER `locations`;

-- 添加 emby_item 表的 server_id 字段
ALTER TABLE `emby_item`
ADD COLUMN `server_id` varchar(100) DEFAULT NULL COMMENT 'Emby服务器ID' AFTER `media_sources`;

-- 添加 emby_genre 表的 server_id 字段
ALTER TABLE `emby_genre`
ADD COLUMN `server_id` varchar(100) DEFAULT NULL COMMENT 'Emby服务器ID' AFTER `item_count`;

-- 添加 emby_tag 表的 server_id 字段
ALTER TABLE `emby_tag`
ADD COLUMN `server_id` varchar(100) DEFAULT NULL COMMENT 'Emby服务器ID' AFTER `item_count`;

-- 添加 emby_studio 表的 server_id 字段
ALTER TABLE `emby_studio`
ADD COLUMN `server_id` varchar(100) DEFAULT NULL COMMENT 'Emby服务器ID' AFTER `item_count`;

-- 添加索引（如果已存在会报错，可以忽略）
ALTER TABLE `emby_library` ADD INDEX `idx_server_id` (`server_id`);
ALTER TABLE `emby_item` ADD INDEX `idx_server_id` (`server_id`);
ALTER TABLE `emby_genre` ADD INDEX `idx_server_id` (`server_id`);
ALTER TABLE `emby_tag` ADD INDEX `idx_server_id` (`server_id`);
ALTER TABLE `emby_studio` ADD INDEX `idx_server_id` (`server_id`);
