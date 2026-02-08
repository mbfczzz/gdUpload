-- Emby下载历史表
CREATE TABLE IF NOT EXISTS `emby_download_history` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `emby_item_id` varchar(100) NOT NULL COMMENT 'Emby媒体项ID',
  `emby_config_id` bigint(20) NOT NULL COMMENT 'Emby配置ID',
  `download_status` varchar(20) NOT NULL COMMENT '下载状态：success-成功, failed-失败',
  `file_path` text COMMENT '文件路径',
  `file_size` bigint(20) DEFAULT NULL COMMENT '文件大小（字节）',
  `error_message` text COMMENT '错误信息',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_emby_item_id` (`emby_item_id`),
  KEY `idx_emby_config_id` (`emby_config_id`),
  KEY `idx_download_status` (`download_status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Emby下载历史表';
