-- Emby 配置表
CREATE TABLE IF NOT EXISTS `emby_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_name` VARCHAR(100) NOT NULL COMMENT '配置名称',
  `server_url` VARCHAR(255) NOT NULL COMMENT 'Emby服务器地址',
  `api_key` VARCHAR(255) DEFAULT NULL COMMENT 'API密钥',
  `username` VARCHAR(100) DEFAULT NULL COMMENT '用户名',
  `password` VARCHAR(255) DEFAULT NULL COMMENT '密码',
  `user_id` VARCHAR(100) DEFAULT NULL COMMENT '用户ID',
  `timeout` INT DEFAULT 30000 COMMENT '超时时间（毫秒）',
  `enabled` TINYINT(1) DEFAULT 1 COMMENT '是否启用（0-禁用 1-启用）',
  `is_default` TINYINT(1) DEFAULT 0 COMMENT '是否默认配置（0-否 1-是）',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_name` (`config_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Emby配置表';

-- 插入默认配置（可选）
INSERT INTO `emby_config` (`config_name`, `server_url`, `username`, `password`, `enabled`, `is_default`, `remark`)
VALUES ('默认配置', 'http://209.146.116.4:8096', 'mbfczzzz', 'mbfczzzz@123', 1, 1, '默认Emby服务器配置');
