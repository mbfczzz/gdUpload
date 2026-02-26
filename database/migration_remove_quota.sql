-- 数据库迁移脚本：删除配额相关字段
-- 执行前请备份数据库！
-- 执行日期：2026-02-25

USE `gd_upload_manager`;

-- 删除 gd_account 表中的配额相关字段
ALTER TABLE `gd_account`
    DROP COLUMN `daily_limit`,
    DROP COLUMN `used_quota`,
    DROP COLUMN `remaining_quota`,
    DROP COLUMN `quota_reset_time`,
    DROP COLUMN `disabled_time`;

-- 更新状态字段的注释（移除状态 2-已达上限）
ALTER TABLE `gd_account`
    MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用 1-启用';

-- 迁移完成
SELECT '配额相关字段删除完成' AS message;
