-- Emby媒体库表
CREATE TABLE IF NOT EXISTS `emby_library` (
  `id` varchar(100) NOT NULL COMMENT '媒体库ID（来自Emby）',
  `name` varchar(200) NOT NULL COMMENT '媒体库名称',
  `collection_type` varchar(50) DEFAULT NULL COMMENT '集合类型：movies, tvshows, music等',
  `item_count` int(11) DEFAULT NULL COMMENT '媒体项数量',
  `locations` text COMMENT '路径列表（JSON数组）',
  `server_id` varchar(100) DEFAULT NULL COMMENT 'Emby服务器ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `last_sync_time` datetime DEFAULT NULL COMMENT '最后同步时间',
  PRIMARY KEY (`id`),
  KEY `idx_collection_type` (`collection_type`),
  KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Emby媒体库表';

-- Emby媒体项表
CREATE TABLE IF NOT EXISTS `emby_item` (
  `id` varchar(100) NOT NULL COMMENT '媒体项ID（来自Emby）',
  `name` varchar(500) NOT NULL COMMENT '名称',
  `original_title` varchar(500) DEFAULT NULL COMMENT '原始名称',
  `type` varchar(50) NOT NULL COMMENT '类型：Movie, Series, Episode等',
  `parent_id` varchar(100) DEFAULT NULL COMMENT '父级ID（媒体库ID）',
  `production_year` int(11) DEFAULT NULL COMMENT '年份',
  `community_rating` decimal(3,1) DEFAULT NULL COMMENT '评分',
  `official_rating` varchar(50) DEFAULT NULL COMMENT '分级',
  `overview` text COMMENT '简介',
  `genres` text COMMENT '类型列表（JSON数组）',
  `tags` text COMMENT '标签列表（JSON数组）',
  `studios` text COMMENT '工作室列表（JSON数组）',
  `people` text COMMENT '演员列表（JSON数组）',
  `path` text COMMENT '文件路径',
  `size` bigint(20) DEFAULT NULL COMMENT '文件大小（字节）',
  `play_count` int(11) DEFAULT 0 COMMENT '播放次数',
  `media_sources` text COMMENT '媒体源信息（JSON）',
  `server_id` varchar(100) DEFAULT NULL COMMENT 'Emby服务器ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `last_sync_time` datetime DEFAULT NULL COMMENT '最后同步时间',
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`(100)),
  KEY `idx_type` (`type`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_production_year` (`production_year`),
  KEY `idx_update_time` (`update_time`),
  FULLTEXT KEY `ft_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Emby媒体项表';

-- Emby类型表
CREATE TABLE IF NOT EXISTS `emby_genre` (
  `id` varchar(100) NOT NULL COMMENT '类型ID（来自Emby）',
  `name` varchar(200) NOT NULL COMMENT '类型名称',
  `item_count` int(11) DEFAULT NULL COMMENT '媒体项数量',
  `server_id` varchar(100) DEFAULT NULL COMMENT 'Emby服务器ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `last_sync_time` datetime DEFAULT NULL COMMENT '最后同步时间',
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`),
  KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Emby类型表';

-- Emby标签表
CREATE TABLE IF NOT EXISTS `emby_tag` (
  `id` varchar(100) NOT NULL COMMENT '标签ID（来自Emby）',
  `name` varchar(200) NOT NULL COMMENT '标签名称',
  `item_count` int(11) DEFAULT NULL COMMENT '媒体项数量',
  `server_id` varchar(100) DEFAULT NULL COMMENT 'Emby服务器ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `last_sync_time` datetime DEFAULT NULL COMMENT '最后同步时间',
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`),
  KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Emby标签表';

-- Emby工作室表
CREATE TABLE IF NOT EXISTS `emby_studio` (
  `id` varchar(100) NOT NULL COMMENT '工作室ID（来自Emby）',
  `name` varchar(200) NOT NULL COMMENT '工作室名称',
  `item_count` int(11) DEFAULT NULL COMMENT '媒体项数量',
  `server_id` varchar(100) DEFAULT NULL COMMENT 'Emby服务器ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `last_sync_time` datetime DEFAULT NULL COMMENT '最后同步时间',
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`),
  KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Emby工作室表';

-- Emby服务器信息表
CREATE TABLE IF NOT EXISTS `emby_server_info` (
  `id` varchar(100) NOT NULL COMMENT '服务器ID（来自Emby）',
  `server_name` varchar(200) NOT NULL COMMENT '服务器名称',
  `version` varchar(50) DEFAULT NULL COMMENT '版本',
  `operating_system` varchar(100) DEFAULT NULL COMMENT '操作系统',
  `local_address` varchar(200) DEFAULT NULL COMMENT '本地地址',
  `wan_address` varchar(200) DEFAULT NULL COMMENT '外网地址',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `last_sync_time` datetime DEFAULT NULL COMMENT '最后同步时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Emby服务器信息表';
