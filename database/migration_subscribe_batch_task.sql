-- 订阅批量搜索任务表
CREATE TABLE IF NOT EXISTS `subscribe_batch_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `task_name` VARCHAR(255) NOT NULL COMMENT '任务名称',
  `total_count` INT NOT NULL DEFAULT 0 COMMENT '订阅总数',
  `completed_count` INT NOT NULL DEFAULT 0 COMMENT '已完成数量',
  `success_count` INT NOT NULL DEFAULT 0 COMMENT '成功数量',
  `failed_count` INT NOT NULL DEFAULT 0 COMMENT '失败数量',
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态: PENDING-待执行, RUNNING-执行中, PAUSED-已暂停, COMPLETED-已完成, FAILED-失败',
  `delay_min` INT NOT NULL DEFAULT 1 COMMENT '最小延迟(分钟)',
  `delay_max` INT NOT NULL DEFAULT 2 COMMENT '最大延迟(分钟)',
  `json_data` LONGTEXT COMMENT '订阅JSON数据',
  `start_time` DATETIME COMMENT '开始时间',
  `end_time` DATETIME COMMENT '结束时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订阅批量搜索任务表';

-- 订阅批量搜索任务日志表
CREATE TABLE IF NOT EXISTS `subscribe_batch_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `task_id` BIGINT NOT NULL COMMENT '任务ID',
  `subscribe_id` INT NOT NULL COMMENT '订阅ID',
  `subscribe_name` VARCHAR(255) COMMENT '订阅名称',
  `status` VARCHAR(20) NOT NULL COMMENT '状态: SUCCESS-成功, FAILED-失败',
  `delay_seconds` INT COMMENT '延迟秒数',
  `request_url` VARCHAR(500) COMMENT '请求URL',
  `request_data` TEXT COMMENT '请求数据',
  `response_data` LONGTEXT COMMENT '响应数据',
  `error_message` TEXT COMMENT '错误信息',
  `execute_time` DATETIME NOT NULL COMMENT '执行时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_subscribe_id` (`subscribe_id`),
  KEY `idx_execute_time` (`execute_time`),
  CONSTRAINT `fk_batch_log_task` FOREIGN KEY (`task_id`) REFERENCES `subscribe_batch_task` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订阅批量搜索任务日志表';
