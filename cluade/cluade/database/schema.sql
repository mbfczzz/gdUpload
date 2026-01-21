-- Google Drive 多账号上传管理系统数据库表结构
-- 数据库版本: MySQL 8.0+

-- 创建数据库
CREATE DATABASE IF NOT EXISTS `gd_upload_manager` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `gd_upload_manager`;

-- 1. Google Drive 账号表
CREATE TABLE `gd_account` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `account_name` VARCHAR(100) NOT NULL COMMENT '账号名称',
    `account_email` VARCHAR(100) NOT NULL COMMENT '账号邮箱',
    `rclone_config_name` VARCHAR(100) NOT NULL COMMENT 'rclone配置名称',
    `daily_limit` BIGINT NOT NULL DEFAULT 805306368000 COMMENT '每日上传限制(字节) 默认750GB',
    `used_quota` BIGINT NOT NULL DEFAULT 0 COMMENT '已使用配额(字节)',
    `remaining_quota` BIGINT NOT NULL DEFAULT 805306368000 COMMENT '剩余配额(字节)',
    `quota_reset_time` DATETIME NULL COMMENT '配额重置时间',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用 1-启用 2-已达上限',
    `priority` INT NOT NULL DEFAULT 0 COMMENT '优先级，数字越大优先级越高',
    `remark` VARCHAR(500) NULL COMMENT '备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_account_email` (`account_email`),
    UNIQUE KEY `uk_rclone_config_name` (`rclone_config_name`),
    KEY `idx_status` (`status`),
    KEY `idx_priority` (`priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Google Drive账号表';

-- 2. 上传任务表
CREATE TABLE `upload_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_name` VARCHAR(200) NOT NULL COMMENT '任务名称',
    `task_type` TINYINT NOT NULL DEFAULT 1 COMMENT '任务类型: 1-普通上传 2-增量上传',
    `source_path` VARCHAR(500) NOT NULL COMMENT '源文件路径',
    `target_path` VARCHAR(500) NOT NULL COMMENT '目标路径',
    `total_count` INT NOT NULL DEFAULT 0 COMMENT '总文件数',
    `uploaded_count` INT NOT NULL DEFAULT 0 COMMENT '已上传文件数',
    `failed_count` INT NOT NULL DEFAULT 0 COMMENT '失败文件数',
    `total_size` BIGINT NOT NULL DEFAULT 0 COMMENT '总大小(字节)',
    `uploaded_size` BIGINT NOT NULL DEFAULT 0 COMMENT '已上传大小(字节)',
    `progress` INT NOT NULL DEFAULT 0 COMMENT '进度百分比(0-100)',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0-待开始 1-上传中 2-已完成 3-已暂停 4-已取消 5-失败',
    `current_account_id` BIGINT NULL COMMENT '当前使用的账号ID',
    `start_time` DATETIME NULL COMMENT '开始时间',
    `end_time` DATETIME NULL COMMENT '结束时间',
    `error_message` TEXT NULL COMMENT '错误信息',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    KEY `fk_current_account` (`current_account_id`),
    CONSTRAINT `fk_task_account` FOREIGN KEY (`current_account_id`) REFERENCES `gd_account` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='上传任务表';

-- 3. 文件信息表
CREATE TABLE `file_info` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id` BIGINT NOT NULL COMMENT '任务ID',
    `file_path` VARCHAR(1000) NOT NULL COMMENT '文件路径',
    `file_name` VARCHAR(500) NOT NULL COMMENT '文件名',
    `file_size` BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小(字节)',
    `file_md5` VARCHAR(32) NULL COMMENT '文件MD5',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0-待上传 1-上传中 2-已上传 3-失败 4-跳过',
    `upload_account_id` BIGINT NULL COMMENT '上传使用的账号ID',
    `upload_start_time` DATETIME NULL COMMENT '上传开始时间',
    `upload_end_time` DATETIME NULL COMMENT '上传结束时间',
    `retry_count` INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    `error_message` TEXT NULL COMMENT '错误信息',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_status` (`status`),
    KEY `idx_file_md5` (`file_md5`),
    KEY `fk_upload_account` (`upload_account_id`),
    CONSTRAINT `fk_file_task` FOREIGN KEY (`task_id`) REFERENCES `upload_task` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_file_account` FOREIGN KEY (`upload_account_id`) REFERENCES `gd_account` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件信息表';

-- 4. 上传记录表
CREATE TABLE `upload_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id` BIGINT NOT NULL COMMENT '任务ID',
    `account_id` BIGINT NOT NULL COMMENT '账号ID',
    `file_id` BIGINT NULL COMMENT '文件ID',
    `upload_size` BIGINT NOT NULL DEFAULT 0 COMMENT '上传大小(字节)',
    `upload_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-成功 2-失败',
    `error_message` TEXT NULL COMMENT '错误信息',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_account_id` (`account_id`),
    KEY `idx_file_id` (`file_id`),
    KEY `idx_upload_time` (`upload_time`),
    CONSTRAINT `fk_record_task` FOREIGN KEY (`task_id`) REFERENCES `upload_task` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_record_account` FOREIGN KEY (`account_id`) REFERENCES `gd_account` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_record_file` FOREIGN KEY (`file_id`) REFERENCES `file_info` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='上传记录表';

-- 5. 账号使用统计表
CREATE TABLE `account_usage_stats` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `account_id` BIGINT NOT NULL COMMENT '账号ID',
    `stat_date` DATE NOT NULL COMMENT '统计日期',
    `upload_size` BIGINT NOT NULL DEFAULT 0 COMMENT '上传大小(字节)',
    `upload_files` INT NOT NULL DEFAULT 0 COMMENT '上传文件数',
    `success_files` INT NOT NULL DEFAULT 0 COMMENT '成功文件数',
    `failed_files` INT NOT NULL DEFAULT 0 COMMENT '失败文件数',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_account_date` (`account_id`, `stat_date`),
    KEY `idx_stat_date` (`stat_date`),
    CONSTRAINT `fk_stats_account` FOREIGN KEY (`account_id`) REFERENCES `gd_account` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账号使用统计表';

-- 6. 系统日志表
CREATE TABLE `system_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `log_type` TINYINT NOT NULL COMMENT '日志类型: 1-信息 2-警告 3-错误 4-调试',
    `log_level` VARCHAR(20) NOT NULL COMMENT '日志级别: INFO, WARN, ERROR, DEBUG',
    `module` VARCHAR(50) NOT NULL COMMENT '模块名称',
    `operation` VARCHAR(100) NOT NULL COMMENT '操作名称',
    `task_id` BIGINT NULL COMMENT '关联任务ID',
    `account_id` BIGINT NULL COMMENT '关联账号ID',
    `message` TEXT NOT NULL COMMENT '日志消息',
    `detail` TEXT NULL COMMENT '详细信息',
    `ip_address` VARCHAR(50) NULL COMMENT 'IP地址',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_log_type` (`log_type`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_account_id` (`account_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_module` (`module`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统日志表';

-- 7. 系统配置表
CREATE TABLE `system_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `config_key` VARCHAR(100) NOT NULL COMMENT '配置键',
    `config_value` TEXT NOT NULL COMMENT '配置值',
    `config_type` VARCHAR(20) NOT NULL DEFAULT 'string' COMMENT '配置类型: string, number, boolean, json',
    `description` VARCHAR(500) NULL COMMENT '配置描述',
    `is_system` TINYINT NOT NULL DEFAULT 0 COMMENT '是否系统配置: 0-否 1-是',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- 插入默认系统配置
INSERT INTO `system_config` (`config_key`, `config_value`, `config_type`, `description`, `is_system`) VALUES
('rclone.path', '/usr/bin/rclone', 'string', 'rclone可执行文件路径', 1),
('rclone.config.path', '~/.config/rclone/rclone.conf', 'string', 'rclone配置文件路径', 1),
('upload.concurrent.files', '3', 'number', '并发上传文件数', 0),
('upload.retry.times', '3', 'number', '上传失败重试次数', 0),
('upload.check.interval', '60', 'number', '上传检查间隔(秒)', 0),
('account.daily.limit', '805306368000', 'number', '账号每日上传限制(字节) 750GB', 0),
('account.warning.threshold', '0.9', 'number', '账号配额警告阈值(90%)', 0),
('log.retention.days', '30', 'number', '日志保留天数', 0);

-- 创建视图：任务统计视图
CREATE OR REPLACE VIEW `v_task_statistics` AS
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

-- 创建视图：账号使用情况视图
CREATE OR REPLACE VIEW `v_account_usage` AS
SELECT
    a.id AS account_id,
    a.account_name,
    a.account_email,
    a.status,
    a.daily_limit,
    a.used_quota,
    a.remaining_quota,
    ROUND(a.used_quota * 100.0 / a.daily_limit, 2) AS usage_percent,
    COUNT(DISTINCT ur.task_id) AS task_count,
    SUM(ur.upload_size) AS total_uploaded,
    a.quota_reset_time
FROM gd_account a
LEFT JOIN upload_record ur ON a.id = ur.account_id AND DATE(ur.upload_time) = CURDATE()
GROUP BY a.id;

-- 创建存储过程：重置账号配额
DELIMITER //
CREATE PROCEDURE `sp_reset_account_quota`()
BEGIN
    UPDATE gd_account
    SET
        used_quota = 0,
        remaining_quota = daily_limit,
        quota_reset_time = DATE_ADD(NOW(), INTERVAL 1 DAY),
        status = IF(status = 2, 1, status)
    WHERE quota_reset_time IS NULL OR quota_reset_time <= NOW();
END //
DELIMITER ;

-- 创建存储过程：更新任务统计
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

-- 创建定时任务事件：每天凌晨重置账号配额
SET GLOBAL event_scheduler = ON;

DELIMITER //
CREATE EVENT IF NOT EXISTS `evt_reset_daily_quota`
ON SCHEDULE EVERY 1 DAY
STARTS CONCAT(CURDATE() + INTERVAL 1 DAY, ' 00:00:00')
DO
BEGIN
    CALL sp_reset_account_quota();
    INSERT INTO system_log (log_type, log_level, module, operation, message)
    VALUES (1, 'INFO', 'SYSTEM', 'RESET_QUOTA', '每日账号配额已重置');
END //
DELIMITER ;

-- 创建索引优化查询性能
CREATE INDEX idx_file_task_status ON file_info(task_id, status);
CREATE INDEX idx_record_account_time ON upload_record(account_id, upload_time);
CREATE INDEX idx_log_create_time ON system_log(create_time);
