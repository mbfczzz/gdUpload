-- 修改缓存表，将server_id改为config_id，关联emby_config表

-- 修改emby_library表
ALTER TABLE `emby_library`
  CHANGE COLUMN `server_id` `config_id` BIGINT DEFAULT NULL COMMENT 'Emby配置ID（关联emby_config表）',
  ADD KEY `idx_config_id` (`config_id`);

-- 修改emby_item表
ALTER TABLE `emby_item`
  CHANGE COLUMN `server_id` `config_id` BIGINT DEFAULT NULL COMMENT 'Emby配置ID（关联emby_config表）',
  ADD KEY `idx_config_id` (`config_id`);

-- 修改emby_genre表
ALTER TABLE `emby_genre`
  CHANGE COLUMN `server_id` `config_id` BIGINT DEFAULT NULL COMMENT 'Emby配置ID（关联emby_config表）',
  ADD KEY `idx_config_id` (`config_id`);

-- 修改emby_tag表
ALTER TABLE `emby_tag`
  CHANGE COLUMN `server_id` `config_id` BIGINT DEFAULT NULL COMMENT 'Emby配置ID（关联emby_config表）',
  ADD KEY `idx_config_id` (`config_id`);

-- 修改emby_studio表
ALTER TABLE `emby_studio`
  CHANGE COLUMN `server_id` `config_id` BIGINT DEFAULT NULL COMMENT 'Emby配置ID（关联emby_config表）',
  ADD KEY `idx_config_id` (`config_id`);

-- 修改emby_server_info表
ALTER TABLE `emby_server_info`
  ADD COLUMN `config_id` BIGINT DEFAULT NULL COMMENT 'Emby配置ID（关联emby_config表）' AFTER `id`,
  ADD KEY `idx_config_id` (`config_id`);
