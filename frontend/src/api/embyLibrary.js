import request from '@/utils/request'

/**
 * Emby库检查 API
 */

/** 扫描本地STRM目录并返回文件树 + 验证结果 */
export function inspectLibrary(localPath) {
  return request.get('/emby-library/inspect', { params: { localPath } })
}

/** 返回汇总统计 */
export function getInspectSummary(localPath) {
  return request.get('/emby-library/summary', { params: { localPath } })
}
