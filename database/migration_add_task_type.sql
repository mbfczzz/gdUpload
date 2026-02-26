-- 添加任务类型字段到emby_download_history表
ALTER TABLE emby_download_history
ADD COLUMN task_type VARCHAR(50) DEFAULT 'download' COMMENT '任务类型：download-仅下载, download_upload-下载并上传';

-- 更新现有数据为默认值
UPDATE emby_download_history SET task_type = 'download' WHERE task_type IS NULL;
