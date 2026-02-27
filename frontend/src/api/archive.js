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
