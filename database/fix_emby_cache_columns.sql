-- 修复Emby缓存表，添加缺失的字段

-- 检查并添加 emby_library 表的 server_id 字段
ALTER TABLE `emby_library`
ADD COLUMN IF NOT EXISTS `server_id` varchar(100) DEFAULT NULL COMMENT 'Emby服务器ID' AFTER `locations`;

-- 检查并添加 emby_item 表的 server_id 字段
ALTER TABLE `emby_item`
ADD COLUMN IF NOT EXISTS `server_id` varchar(100) DEFAULT NULL COMMENT 'Emby服务器ID' AFTER `media_sources`;

-- 检查并添加 emby_genre 表的 server_id 字段
ALTER TABLE `emby_genre`
ADD COLUMN IF NOT EXISTS `server_id` varchar(100) DEFAULT NULL COMMENT 'Emby服务器ID' AFTER `item_count`;

-- 检查并添加 emby_tag 表的 server_id 字段
ALTER TABLE `emby_tag`
ADD COLUMN IF NOT EXISTS `server_id` varchar(100) DEFAULT NULL COMMENT 'Emby服务器ID' AFTER `item_count`;

-- 检查并添加 emby_studio 表的 server_id 字段
ALTER TABLE `emby_studio`
ADD COLUMN IF NOT EXISTS `server_id` varchar(100) DEFAULT NULL COMMENT 'Emby服务器ID' AFTER `item_count`;

-- 添加索引
ALTER TABLE `emby_library` ADD INDEX IF NOT EXISTS `idx_server_id` (`server_id`);
ALTER TABLE `emby_item` ADD INDEX IF NOT EXISTS `idx_server_id` (`server_id`);
ALTER TABLE `emby_genre` ADD INDEX IF NOT EXISTS `idx_server_id` (`server_id`);
ALTER TABLE `emby_tag` ADD INDEX IF NOT EXISTS `idx_server_id` (`server_id`);
ALTER TABLE `emby_studio` ADD INDEX IF NOT EXISTS `idx_server_id` (`server_id`);
