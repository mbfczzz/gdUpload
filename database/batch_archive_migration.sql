-- 批量归档功能数据库迁移脚本
-- 执行前请备份数据库

-- 1. 创建批量归档任务表
CREATE TABLE IF NOT EXISTS `archive_batch_task` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_name`           VARCHAR(200) NOT NULL                COMMENT '任务名称（自动生成）',
  `rclone_config_name`  VARCHAR(100)     NULL                COMMENT 'rclone 配置名',
  `source_path`         VARCHAR(500)     NULL                COMMENT '源目录路径（相对远程根目录）',
  `status`              VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/COMPLETED/PARTIAL/FAILED',
  `total_files`         INT          NOT NULL DEFAULT 0      COMMENT '媒体文件总数',
  `processed_files`     INT          NOT NULL DEFAULT 0      COMMENT '已处理文件数',
  `success_count`       INT          NOT NULL DEFAULT 0      COMMENT '归档成功数',
  `failed_count`        INT          NOT NULL DEFAULT 0      COMMENT '归档失败数',
  `manual_count`        INT          NOT NULL DEFAULT 0      COMMENT '待人工处理数',
  `current_file`        VARCHAR(500)     NULL                COMMENT '当前处理中的文件名',
  `error_message`       TEXT             NULL                COMMENT '任务级错误信息',
  `deleted`             TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `create_time`         DATETIME         NULL                COMMENT '创建时间',
  `update_time`         DATETIME         NULL                COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批量归档任务';

-- 2. 给归档历史表添加批量任务关联字段
ALTER TABLE `archive_history`
  ADD COLUMN `batch_task_id` BIGINT NULL COMMENT '所属批量任务ID（单文件归档时为空）' AFTER `remark`;
