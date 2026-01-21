-- 迁移脚本: 从固定时间重置改为滚动24小时窗口
-- 执行时间: 2026-01-19
-- 说明: 将Google Drive配额管理从固定每日重置改为滚动24小时窗口

USE `gd_upload_manager`;

-- 1. 禁用并删除定时任务事件
DROP EVENT IF EXISTS `evt_reset_daily_quota`;

-- 2. 删除固定时间重置的存储过程
DROP PROCEDURE IF EXISTS `sp_reset_account_quota`;

-- 3. 删除不再需要的字段
-- 注意: quota_reset_time字段不再需要
ALTER TABLE `gd_account` DROP COLUMN IF EXISTS `quota_reset_time`;

-- 4. 将used_quota和remaining_quota字段设置为0(这些字段现在通过实时计算获得)
-- 保留这些字段是为了向后兼容,但它们的值将被忽略
UPDATE `gd_account` SET `used_quota` = 0, `remaining_quota` = `daily_limit`;

-- 5. 添加注释说明这些字段已废弃
ALTER TABLE `gd_account`
  MODIFY COLUMN `used_quota` BIGINT NOT NULL DEFAULT 0 COMMENT '已使用配额(字节) - 已废弃,通过upload_record实时计算',
  MODIFY COLUMN `remaining_quota` BIGINT NOT NULL DEFAULT 805306368000 COMMENT '剩余配额(字节) - 已废弃,通过upload_record实时计算';

-- 6. 确保upload_record表有正确的索引用于24小时窗口查询
-- 这个索引对于性能至关重要
CREATE INDEX IF NOT EXISTS `idx_account_time_status` ON `upload_record`(`account_id`, `upload_time`, `status`);

-- 7. 重新创建账号使用情况视图,使用滚动24小时窗口
DROP VIEW IF EXISTS `v_account_usage`;
CREATE OR REPLACE VIEW `v_account_usage` AS
SELECT
    a.id AS account_id,
    a.account_name,
    a.account_email,
    a.status,
    a.daily_limit,
    COALESCE((SELECT SUM(r.upload_size)
              FROM upload_record r
              WHERE r.account_id = a.id
                AND r.upload_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
                AND r.status = 1), 0) AS used_quota,
    a.daily_limit - COALESCE((SELECT SUM(r.upload_size)
                              FROM upload_record r
                              WHERE r.account_id = a.id
                                AND r.upload_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
                                AND r.status = 1), 0) AS remaining_quota,
    ROUND(COALESCE((SELECT SUM(r.upload_size)
                    FROM upload_record r
                    WHERE r.account_id = a.id
                      AND r.upload_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
                      AND r.status = 1), 0) * 100.0 / a.daily_limit, 2) AS usage_percent,
    COUNT(DISTINCT ur.task_id) AS task_count,
    SUM(ur.upload_size) AS total_uploaded_today
FROM gd_account a
LEFT JOIN upload_record ur ON a.id = ur.account_id AND DATE(ur.upload_time) = CURDATE()
GROUP BY a.id;

-- 8. 自动恢复状态为2(已达上限)但实际配额已恢复的账号
UPDATE `gd_account` a
SET a.status = 1
WHERE a.status = 2
  AND (a.daily_limit - COALESCE((SELECT SUM(r.upload_size)
                                 FROM upload_record r
                                 WHERE r.account_id = a.id
                                   AND r.upload_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
                                   AND r.status = 1), 0)) > 0;

-- 9. 插入迁移日志
INSERT INTO `system_log` (`log_type`, `log_level`, `module`, `operation`, `message`, `detail`)
VALUES (1, 'INFO', 'MIGRATION', 'ROLLING_WINDOW',
        '配额管理已迁移到滚动24小时窗口',
        '删除了固定时间重置机制,改为实时计算过去24小时的上传量');

-- 10. 显示迁移结果
SELECT
    '迁移完成' AS status,
    COUNT(*) AS total_accounts,
    SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END) AS enabled_accounts,
    SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END) AS quota_exceeded_accounts,
    SUM(CASE WHEN status = 0 THEN 1 ELSE 0 END) AS disabled_accounts
FROM `gd_account`;

-- 完成
SELECT '滚动24小时窗口迁移已完成' AS message;
