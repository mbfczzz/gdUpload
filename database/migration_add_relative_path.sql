-- 数据库迁移脚本：添加 relative_path 字段
-- 用途：支持上传时保留目录结构
-- 日期：2026-02-07

USE `gd_upload_manager`;

-- 添加 relative_path 字段到 file_info 表
ALTER TABLE `file_info`
ADD COLUMN `relative_path` VARCHAR(1000) NULL COMMENT '相对路径（相对于任务源路径）'
AFTER `file_name`;

-- 验证字段添加成功
DESC file_info;

-- 查看示例数据
SELECT
    id,
    file_name,
    relative_path,
    file_path
FROM file_info
LIMIT 10;
