-- 修复已有的归档历史记录：从关联的批量任务中回填 rclone_config_name
-- 问题：BatchArchiveServiceImpl 标记 manual_required / failed 时漏存了 rclone_config_name
-- 导致用户点"手动归档"重试时，后端把云盘路径当本地路径处理，报 NoSuchFileException

UPDATE archive_history h
INNER JOIN archive_batch_task t ON h.batch_task_id = t.id
SET h.rclone_config_name = t.rclone_config_name
WHERE h.rclone_config_name IS NULL
  AND t.rclone_config_name IS NOT NULL
  AND h.deleted = 0;
