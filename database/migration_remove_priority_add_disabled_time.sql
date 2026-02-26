-- 数据库迁移脚本：删除优先级字段，添加禁用时间字段
-- 执行前请备份数据库！
-- 执行日期：2026-02-25

USE `gd_upload_manager`;

-- 删除 gd_account 表中的优先级字段
ALTER TABLE `gd_account`
    DROP COLUMN `priority`,
    DROP INDEX `idx_priority`;

-- 添加禁用时间字段
ALTER TABLE `gd_account`
    ADD COLUMN `disabled_time` DATETIME NULL COMMENT '禁用时间（账号被禁用的时间）' AFTER `status`;

-- 迁移完成
SELECT '优先级字段已删除，禁用时间字段已添加' AS message;
