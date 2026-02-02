-- 修复 json_data 字段类型（从 TEXT 改为 LONGTEXT）
ALTER TABLE `subscribe_batch_task`
MODIFY COLUMN `json_data` LONGTEXT COMMENT '订阅JSON数据';

-- 修复 response_data 字段类型（从 TEXT 改为 LONGTEXT）
ALTER TABLE `subscribe_batch_log`
MODIFY COLUMN `response_data` LONGTEXT COMMENT '响应数据';
