-- 修复 STRM 文件记录唯一键冲突问题
-- 问题：rel_file_path(200) 前缀对长路径会冲突
-- 方案：添加 path_hash 字段存储完整路径的 MD5，用作唯一键

-- 1. 添加 path_hash 字段
ALTER TABLE `strm_file_record`
ADD COLUMN `path_hash` CHAR(32) NULL COMMENT '路径MD5哈希（唯一键用）' AFTER `rel_file_path`;

-- 2. 为现有数据填充 path_hash
UPDATE `strm_file_record`
SET `path_hash` = MD5(CONCAT(watch_config_id, ':', rel_file_path))
WHERE `path_hash` IS NULL;

-- 3. 删除旧的唯一键
ALTER TABLE `strm_file_record`
DROP INDEX `uk_config_path`;

-- 4. 添加新的唯一键（基于 path_hash）
ALTER TABLE `strm_file_record`
ADD UNIQUE KEY `uk_path_hash` (`path_hash`);

-- 5. 添加索引加速查询
ALTER TABLE `strm_file_record`
ADD INDEX `idx_config_path` (`watch_config_id`, `rel_file_path`(200));
