-- 添加 disabled_time 字段到 gd_account 表
-- 用于记录账号被禁用的时间，解封时间 = 禁用时间 + 24小时
-- 版本: v1.3
-- 日期: 2026-01-20

USE `gd_upload_manager`;

-- 添加 disabled_time 字段
ALTER TABLE `gd_account`
ADD COLUMN `disabled_time` DATETIME NULL COMMENT '禁用时间（账号被封禁的时间）' AFTER `quota_reset_time`;

-- 更新注释
ALTER TABLE `gd_account`
MODIFY COLUMN `quota_reset_time` DATETIME NULL COMMENT '配额重置时间（预计解封时间）';

-- 说明：
-- 1. disabled_time: 记录账号被禁用的时间点
-- 2. quota_reset_time: 预计解封时间 = disabled_time + 24小时
-- 3. 当账号被启用时，这两个字段都会被清空
