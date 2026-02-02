-- 检查表结构和数据状态
-- 用于诊断当前表的状态

-- 1. 检查 emby_config 表
SELECT '=== emby_config 表 ===' AS info;
SELECT id, name, is_default FROM emby_config;

-- 2. 检查各表的主键信息
SELECT '=== 主键信息 ===' AS info;
SELECT
    TABLE_NAME,
    COLUMN_NAME,
    CONSTRAINT_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = 'gd_upload_manager'
  AND TABLE_NAME IN ('emby_library', 'emby_item', 'emby_genre', 'emby_tag', 'emby_studio')
  AND CONSTRAINT_NAME = 'PRIMARY'
ORDER BY TABLE_NAME, ORDINAL_POSITION;

-- 3. 检查 emby_config_id 字段是否存在
SELECT '=== emby_config_id 字段信息 ===' AS info;
SELECT
    TABLE_NAME,
    COLUMN_NAME,
    IS_NULLABLE,
    COLUMN_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'gd_upload_manager'
  AND TABLE_NAME IN ('emby_library', 'emby_item', 'emby_genre', 'emby_tag', 'emby_studio')
  AND COLUMN_NAME = 'emby_config_id'
ORDER BY TABLE_NAME;

-- 4. 检查 NULL 值数量
SELECT '=== NULL 值统计 ===' AS info;
SELECT
    'emby_library' AS table_name,
    COUNT(*) AS total_rows,
    SUM(CASE WHEN emby_config_id IS NULL THEN 1 ELSE 0 END) AS null_count
FROM emby_library
UNION ALL
SELECT
    'emby_item' AS table_name,
    COUNT(*) AS total_rows,
    SUM(CASE WHEN emby_config_id IS NULL THEN 1 ELSE 0 END) AS null_count
FROM emby_item
UNION ALL
SELECT
    'emby_genre' AS table_name,
    COUNT(*) AS total_rows,
    SUM(CASE WHEN emby_config_id IS NULL THEN 1 ELSE 0 END) AS null_count
FROM emby_genre
UNION ALL
SELECT
    'emby_tag' AS table_name,
    COUNT(*) AS total_rows,
    SUM(CASE WHEN emby_config_id IS NULL THEN 1 ELSE 0 END) AS null_count
FROM emby_tag
UNION ALL
SELECT
    'emby_studio' AS table_name,
    COUNT(*) AS total_rows,
    SUM(CASE WHEN emby_config_id IS NULL THEN 1 ELSE 0 END) AS null_count
FROM emby_studio;
