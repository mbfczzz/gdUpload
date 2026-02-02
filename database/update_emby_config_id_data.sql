-- 智能修复 emby_config_id 迁移脚本
-- 自动检测表结构并执行相应操作
-- 执行时间: 2026-02-02

-- 获取默认配置的 ID
SET @default_config_id = (SELECT id FROM emby_config WHERE is_default = 1 LIMIT 1);
SET @default_config_id = IFNULL(@default_config_id, (SELECT id FROM emby_config ORDER BY id LIMIT 1));

SELECT CONCAT('使用配置 ID: ', @default_config_id) AS info;

-- 更新所有 NULL 值
UPDATE `emby_library` SET `emby_config_id` = @default_config_id WHERE `emby_config_id` IS NULL;
UPDATE `emby_item` SET `emby_config_id` = @default_config_id WHERE `emby_config_id` IS NULL;
UPDATE `emby_genre` SET `emby_config_id` = @default_config_id WHERE `emby_config_id` IS NULL;
UPDATE `emby_tag` SET `emby_config_id` = @default_config_id WHERE `emby_config_id` IS NULL;
UPDATE `emby_studio` SET `emby_config_id` = @default_config_id WHERE `emby_config_id` IS NULL;

-- 显示更新结果
SELECT
    (SELECT COUNT(*) FROM emby_library WHERE emby_config_id IS NOT NULL) AS library_count,
    (SELECT COUNT(*) FROM emby_item WHERE emby_config_id IS NOT NULL) AS item_count,
    (SELECT COUNT(*) FROM emby_genre WHERE emby_config_id IS NOT NULL) AS genre_count,
    (SELECT COUNT(*) FROM emby_tag WHERE emby_config_id IS NOT NULL) AS tag_count,
    (SELECT COUNT(*) FROM emby_studio WHERE emby_config_id IS NOT NULL) AS studio_count;

SELECT '数据更新完成！现在请手动检查表结构并修改主键。' AS status;
