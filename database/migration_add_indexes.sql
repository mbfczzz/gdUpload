-- 性能优化：补充关键查询字段的索引
-- 执行前请确认表已存在

-- ── archive_history ──────────────────────────────────────────────────────────
-- 按归档批次查历史（getTaskHistory / batch detail）
ALTER TABLE archive_history
    ADD INDEX idx_batch_task_id (batch_task_id);

-- 按状态过滤 + 时间倒序（getHistory with status filter）
ALTER TABLE archive_history
    ADD INDEX idx_status_create_time (status, create_time);

-- 时间倒序（getHistory without filter）
ALTER TABLE archive_history
    ADD INDEX idx_create_time (create_time);

-- ── format_rename_task ───────────────────────────────────────────────────────
-- 任务列表按时间倒序
ALTER TABLE format_rename_task
    ADD INDEX idx_create_time (create_time);

-- ── format_rename_history ────────────────────────────────────────────────────
-- 按任务查明细（最常用）
ALTER TABLE format_rename_history
    ADD INDEX idx_task_id (task_id);

-- 按任务 + 状态过滤（带 status 筛选的明细查询）
ALTER TABLE format_rename_history
    ADD INDEX idx_task_id_status (task_id, status);
