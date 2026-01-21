-- 数据库迁移脚本 v1.2
-- 为 system_log 表添加文件相关字段以支持详细的文件上传日志
-- 执行日期: 2026-01-19

USE `gd_upload_manager`;

-- 1. 为 system_log 表添加文件相关字段
ALTER TABLE `system_log`
    ADD COLUMN `file_id` BIGINT NULL COMMENT '关联文件ID' AFTER `account_id`,
    ADD COLUMN `file_name` VARCHAR(500) NULL COMMENT '文件名称' AFTER `file_id`,
    ADD COLUMN `file_size` BIGINT NULL COMMENT '文件大小(字节)' AFTER `file_name`;

-- 2. 添加索引以优化查询性能
CREATE INDEX `idx_file_id` ON `system_log`(`file_id`);

-- 迁移完成
SELECT '数据库迁移 v1.2 完成 - 已添加文件相关字段到 system_log 表' AS message;
