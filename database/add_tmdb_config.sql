-- 添加TMDB配置到智能搜索配置表
-- 如果已存在TMDB配置则更新，否则插入

-- 删除旧的TMDB配置（如果存在）
DELETE FROM `smart_search_config` WHERE `config_type` = 'tmdb_config' AND `user_id` = 'default';

-- 插入默认TMDB配置
INSERT INTO `smart_search_config` (`user_id`, `config_name`, `config_type`, `config_data`, `is_active`, `remark`) VALUES
('default', 'TMDB配置', 'tmdb_config', '{
  "tmdbEnabled": false,
  "tmdbApiKey": "",
  "tmdbApiUrl": "https://api.themoviedb.org/3",
  "tmdbLanguage": "zh-CN",
  "tmdbTimeout": 10000,
  "tmdbAutoMatch": true
}', 1, 'TMDB影视数据库配置');

-- 查询验证
SELECT * FROM `smart_search_config` WHERE `config_type` = 'tmdb_config';
