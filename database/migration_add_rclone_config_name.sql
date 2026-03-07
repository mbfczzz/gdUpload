-- 归档历史表添加 rclone_config_name 字段
-- 用于记录云端文件的 rclone 配置名，支持从历史记录重试归档时正确识别云端文件

ALTER TABLE archive_history
ADD COLUMN rclone_config_name VARCHAR(100) DEFAULT NULL
COMMENT 'rclone配置名（云端文件归档时记录，本地文件为空）';
