-- 为 115_resource 表添加 tmdb_id 字段
ALTER TABLE `115_resource`
ADD COLUMN `tmdb_id` VARCHAR(50) NULL COMMENT 'TMDB ID' AFTER `name`,
ADD INDEX `idx_tmdb_id` (`tmdb_id`);

-- 查看表结构
DESC `115_resource`;
