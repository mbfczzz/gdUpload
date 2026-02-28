import request from '@/utils/request'

/**
 * STRM 监控管理 API
 */

/** 分页查询监控配置 */
export function listConfigs(page = 1, size = 20) {
  return request.get('/strm-watch/list', { params: { page, size } })
}

/** 新增监控配置 */
export function addConfig(data) {
  return request.post('/strm-watch/add', data)
}

/** 修改监控配置 */
export function updateConfig(id, data) {
  return request.put(`/strm-watch/${id}`, data)
}

/** 删除监控配置 */
export function deleteConfig(id) {
  return request.delete(`/strm-watch/${id}`)
}

/** 启用 */
export function enableConfig(id) {
  return request.post(`/strm-watch/${id}/enable`)
}

/** 禁用 */
export function disableConfig(id) {
  return request.post(`/strm-watch/${id}/disable`)
}

/** 手动增量同步 */
export function triggerSync(id) {
  return request.post(`/strm-watch/${id}/sync`)
}

/** 强制全量重刮 */
export function triggerForceRescrape(id) {
  return request.post(`/strm-watch/${id}/force-rescrape`)
}

/** 查询文件记录 */
export function listRecords(id, status = '', page = 1, size = 20) {
  return request.get(`/strm-watch/${id}/records`, { params: { status, page, size } })
}

/** 查询同步状态 */
export function getSyncStatus(id) {
  return request.get(`/strm-watch/${id}/sync-status`)
}
