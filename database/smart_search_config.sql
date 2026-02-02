-- 智能搜索配置表
CREATE TABLE IF NOT EXISTS `smart_search_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` varchar(100) DEFAULT 'default' COMMENT '用户ID（预留多用户支持）',
  `config_name` varchar(100) NOT NULL COMMENT '配置名称',
  `config_type` varchar(50) NOT NULL COMMENT '配置类型：cloud_config, ai_config, search_config',
  `config_data` text NOT NULL COMMENT '配置数据（JSON格式）',
  `is_active` tinyint(1) DEFAULT 1 COMMENT '是否启用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_type` (`user_id`, `config_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能搜索配置表';

-- 插入默认配置示例
INSERT INTO `smart_search_config` (`user_id`, `config_name`, `config_type`, `config_data`, `is_active`, `remark`) VALUES
('default', '阿里云盘配置', 'cloud_config', '{
  "name": "阿里云盘",
  "cloudType": "channel_alipan",
  "parentId": "697f2333cd2704159fa446d8bc5077584838e3dc",
  "remark": "默认阿里云盘配置"
}', 1, '默认云盘配置'),
('default', 'AI配置', 'ai_config', '{
  "aiEnabled": true,
  "validateLinks": false
}', 1, 'AI和验证配置'),
('default', '搜索权重配置', 'search_config', '{
  "weights": {
    "titleMatch": 40,
    "resolution": 20,
    "fileSize": 15,
    "tagMatch": 10,
    "sourceCredibility": 10,
    "timeliness": 5
  },
  "maxValidationCount": 20,
  "validationTimeout": 10000,
  "debugMode": false
}', 1, '搜索评分权重配置');
