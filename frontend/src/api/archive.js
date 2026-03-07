import request from '@/utils/request'

/**
 * 归档功能 API
 */

/** 正则解析文件名 */
export function analyzeFilename(filename) {
  return request.get('/archive/analyze', { params: { filename } })
}

/** AI 解析文件名（正则失败时调用） */
export function aiAnalyzeFilename(filename) {
  return request.post('/archive/ai-analyze', { filename })
}

/** TMDB 搜索 */
export function searchTmdb(title, year, type = 'tv') {
  return request.get('/archive/tmdb-search', { params: { title, year, type } })
}

/** 通过 TMDB ID 直接查详情（文件名已含 tmdbid 时使用） */
export function fetchTmdbDetail(tmdbId) {
  return request.get('/archive/tmdb-detail', { params: { tmdbId } })
}

/** 执行归档 */
export function executeArchive(data) {
  return request.post('/archive/execute', data)
}

/** 标记为需要人工处理 */
export function markManual(originalPath, originalFilename, remark) {
  return request.post('/archive/mark-manual', { originalPath, originalFilename, remark })
}

/** 获取分类列表 */
export function getCategories() {
  return request.get('/archive/categories')
}

/** 获取归档历史 */
export function getArchiveHistory(page = 1, size = 20, status = '') {
  return request.get('/archive/history', { params: { page, size, status } })
}

/** 更新人工处理备注 */
export function updateRemark(id, remark) {
  return request.put(`/archive/history/${id}/remark`, { remark })
}

/** 用 ffprobe 探测媒体信息（rcloneConfigName 有值时走 rclone 管道读云端文件） */
export function getMediaInfo(filePath, rcloneConfigName) {
  return request.get('/archive/media-info', { params: { filePath, rcloneConfigName } })
}

// ─── 批量归档任务 ─────────────────────────────────────────────────────────────

/** 启动批量归档任务 */
export function startBatchArchive(accountId, sourcePath) {
  return request.post('/archive/batch/start', { accountId, sourcePath })
}

/** 分页查询批量任务列表 */
export function getBatchTasks(page = 1, size = 20) {
  return request.get('/archive/batch/list', { params: { page, size } })
}

/** 查询单个任务（轮询进度用） */
export function getBatchTask(id) {
  return request.get(`/archive/batch/${id}`)
}

/** 查询任务下的归档历史 */
export function getBatchTaskHistory(id, page = 1, size = 50, status = '') {
  return request.get(`/archive/batch/${id}/history`, { params: { page, size, status } })
}

/** 取消批量任务 */
export function cancelBatchTask(id) {
  return request.delete(`/archive/batch/${id}`)
}

/** 暂停批量任务 */
export function pauseBatchTask(id) {
  return request.post(`/archive/batch/${id}/pause`)
}

/** 恢复批量任务 */
export function resumeBatchTask(id) {
  return request.post(`/archive/batch/${id}/resume`)
}
