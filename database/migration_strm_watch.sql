-- STRM 监控管理模块 — 数据库迁移
-- 执行方式: mysql -u root -p gd_upload_manager < migration_strm_watch.sql

-- 1. 监控配置表
CREATE TABLE IF NOT EXISTS `strm_watch_config` (
    `id`                    BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name`                  VARCHAR(100) NOT NULL COMMENT '配置名称',
    `gd_remote`             VARCHAR(100) NOT NULL COMMENT 'rclone 远程名',
    `gd_source_path`        VARCHAR(500) NOT NULL COMMENT 'GD 源目录',
    `output_path`           VARCHAR(500) NOT NULL COMMENT '本地输出根目录',
    `play_url_base`         VARCHAR(500) NOT NULL COMMENT '播放 URL 前缀',
    `enabled`               TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用: 0-禁用 1-启用',
    `scan_interval_minutes` INT NOT NULL DEFAULT 60 COMMENT '扫描间隔（分钟）',
    `last_scan_time`        DATETIME NULL COMMENT '上次扫描时间',
    `next_scan_time`        DATETIME NULL COMMENT '下次扫描时间',
    `last_new_count`        INT NOT NULL DEFAULT 0 COMMENT '上次扫描新增文件数',
    `last_deleted_count`    INT NOT NULL DEFAULT 0 COMMENT '上次扫描删除文件数',
    `last_updated_count`    INT NOT NULL DEFAULT 0 COMMENT '上次扫描更新文件数',
    `total_files`           INT NOT NULL DEFAULT 0 COMMENT '当前有效文件总数',
    `status`                VARCHAR(20) NOT NULL DEFAULT 'IDLE' COMMENT '状态: IDLE/RUNNING',
    `create_time`           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_enabled` (`enabled`),
    KEY `idx_next_scan` (`next_scan_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='STRM监控配置表';

-- 2. 已处理文件记录表
CREATE TABLE IF NOT EXISTS `strm_file_record` (
    `id`              BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `watch_config_id` BIGINT NOT NULL COMMENT '关联监控配置ID',
    `gd_remote`       VARCHAR(100) NOT NULL COMMENT 'rclone 远程名',
    `rel_file_path`   VARCHAR(1000) NOT NULL COMMENT 'GD 文件相对路径',
    `file_mod_time`   VARCHAR(50) NULL COMMENT 'rclone 返回的修改时间（变更检测用）',
    `strm_local_path` VARCHAR(1000) NULL COMMENT '本地 .strm 路径',
    `nfo_local_path`  VARCHAR(1000) NULL COMMENT '本地 .nfo 路径',
    `show_dir`        VARCHAR(1000) NULL COMMENT '节目根目录（删除时清理用）',
    `tmdb_id`         INT NULL COMMENT 'TMDB ID',
    `status`          VARCHAR(20) NOT NULL DEFAULT 'success' COMMENT '状态: success/failed/deleted',
    `fail_reason`     VARCHAR(500) NULL COMMENT '失败原因',
    `create_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_path` (`watch_config_id`, `rel_file_path`(200)),
    KEY `idx_config_id` (`watch_config_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='STRM文件记录表';
