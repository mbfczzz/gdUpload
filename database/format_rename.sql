-- 格式化命名任务表
CREATE TABLE `format_rename_task` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT,
  `task_name`        VARCHAR(200) NOT NULL                 COMMENT '任务名称',
  `account_id`       BIGINT       NOT NULL                 COMMENT 'GD账号ID',
  `rclone_config_name` VARCHAR(100)                        COMMENT 'rclone配置名',
  `dir_path`         VARCHAR(500)                          COMMENT '扫描目录路径（相对根目录）',
  `status`           VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/PAUSED/COMPLETED/FAILED',
  `total_files`      INT          NOT NULL DEFAULT 0        COMMENT '扫描到的媒体文件总数',
  `processed_files`  INT          NOT NULL DEFAULT 0        COMMENT '已处理文件数',
  `renamed_count`    INT          NOT NULL DEFAULT 0        COMMENT '重命名成功数',
  `skipped_count`    INT          NOT NULL DEFAULT 0        COMMENT '跳过数（已有编码或无变化）',
  `failed_count`     INT          NOT NULL DEFAULT 0        COMMENT '失败数',
  `current_file`     VARCHAR(500)                          COMMENT '当前处理文件名',
  `error_message`    TEXT                                  COMMENT '任务级别错误信息',
  `deleted`          TINYINT      NOT NULL DEFAULT 0        COMMENT '软删除',
  `create_time`      DATETIME                              COMMENT '创建时间',
  `update_time`      DATETIME                              COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_status`      (`status`),
  INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='格式化命名任务';

-- 格式化命名历史（每个文件一条）
CREATE TABLE `format_rename_history` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT,
  `task_id`           BIGINT       NOT NULL                 COMMENT '所属任务ID',
  `original_filename` VARCHAR(500)                          COMMENT '原始文件名',
  `new_filename`      VARCHAR(500)                          COMMENT '新文件名（重命名后）',
  `file_path`         VARCHAR(1000)                         COMMENT '文件在远端的相对路径',
  `status`            VARCHAR(20)  NOT NULL                 COMMENT 'renamed/skipped/failed',
  `skip_reason`       VARCHAR(200)                          COMMENT '跳过原因',
  `fail_reason`       TEXT                                  COMMENT '失败原因',
  `deleted`           TINYINT      NOT NULL DEFAULT 0        COMMENT '软删除',
  `create_time`       DATETIME                              COMMENT '创建时间',
  `update_time`       DATETIME                              COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_task_id` (`task_id`),
  INDEX `idx_status`  (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='格式化命名历史';
