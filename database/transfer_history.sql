-- 转存历史记录表
CREATE TABLE IF NOT EXISTS `transfer_history` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `emby_item_id` varchar(100) NOT NULL COMMENT 'Emby媒体项ID',
  `emby_item_name` varchar(500) NOT NULL COMMENT 'Emby媒体项名称',
  `emby_item_year` int(11) DEFAULT NULL COMMENT '年份',
  `resource_id` varchar(200) DEFAULT NULL COMMENT '资源ID',
  `resource_title` varchar(500) DEFAULT NULL COMMENT '资源标题',
  `resource_url` text COMMENT '资源链接',
  `match_score` decimal(5,2) DEFAULT NULL COMMENT '匹配分数',
  `cloud_type` varchar(100) DEFAULT NULL COMMENT '云盘类型',
  `cloud_name` varchar(100) DEFAULT NULL COMMENT '云盘名称',
  `parent_id` varchar(200) DEFAULT NULL COMMENT '目标目录ID',
  `transfer_status` varchar(50) NOT NULL COMMENT '转存状态：success, failed, pending',
  `transfer_message` text COMMENT '转存结果消息',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_emby_item_id` (`emby_item_id`),
  KEY `idx_status` (`transfer_status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='转存历史记录表';

-- 创建索引以提高查询性能
CREATE INDEX idx_emby_item_status ON transfer_history(emby_item_id, transfer_status);
