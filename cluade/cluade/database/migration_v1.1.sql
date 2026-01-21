-- 数据库迁移脚本 v1.1
-- 更新 upload_task 表字段名称以匹配实体类
-- 执行日期: 2026-01-18

USE `gd_upload_manager`;

-- 1. 重命名字段并添加 progress 字段
ALTER TABLE `upload_task`
    CHANGE COLUMN `total_files` `total_count` INT NOT NULL DEFAULT 0 COMMENT '总文件数',
    CHANGE COLUMN `uploaded_files` `uploaded_count` INT NOT NULL DEFAULT 0 COMMENT '已上传文件数',
    CHANGE COLUMN `failed_files` `failed_count` INT NOT NULL DEFAULT 0 COMMENT '失败文件数',
    ADD COLUMN `progress` INT NOT NULL DEFAULT 0 COMMENT '进度百分比(0-100)' AFTER `uploaded_size`,
    MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0-待开始 1-上传中 2-已完成 3-已暂停 4-已取消 5-失败';

-- 2. 更新视图：任务统计视图
DROP VIEW IF EXISTS `v_task_statistics`;
CREATE VIEW `v_task_statistics` AS
SELECT
    t.id AS task_id,
    t.task_name,
    t.status,
    t.total_count,
    t.uploaded_count,
    t.failed_count,
    t.total_size,
    t.uploaded_size,
    t.progress,
    ROUND(t.uploaded_size * 100.0 / NULLIF(t.total_size, 0), 2) AS progress_percent,
    COUNT(DISTINCT ur.account_id) AS used_accounts,
    t.start_time,
    t.end_time,
    TIMESTAMPDIFF(SECOND, t.start_time, COALESCE(t.end_time, NOW())) AS duration_seconds
FROM upload_task t
LEFT JOIN upload_record ur ON t.id = ur.task_id
GROUP BY t.id;

-- 3. 更新存储过程：更新任务统计
DROP PROCEDURE IF EXISTS `sp_update_task_statistics`;
DELIMITER //
CREATE PROCEDURE `sp_update_task_statistics`(IN p_task_id BIGINT)
BEGIN
    UPDATE upload_task t
    SET
        t.uploaded_count = (SELECT COUNT(*) FROM file_info WHERE task_id = p_task_id AND status = 2),
        t.failed_count = (SELECT COUNT(*) FROM file_info WHERE task_id = p_task_id AND status = 3),
        t.uploaded_size = (SELECT COALESCE(SUM(file_size), 0) FROM file_info WHERE task_id = p_task_id AND status = 2)
    WHERE t.id = p_task_id;
END //
DELIMITER ;

-- 4. 初始化现有任务的 progress 字段
UPDATE `upload_task`
SET `progress` = CASE
    WHEN `total_count` = 0 THEN 0
    ELSE ROUND((`uploaded_count` * 100.0) / `total_count`)
END
WHERE `progress` = 0 AND `total_count` > 0;

-- 迁移完成
SELECT '数据库迁移 v1.1 完成' AS message;
