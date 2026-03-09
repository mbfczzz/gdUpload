import request from '@/utils/request'

/**
 * Emby库检查 API
 */

/** 扫描指定路径并返回文件树 + 验证结果 */
export function inspectLibrary(rcloneRemote, path = '/') {
  return request.get('/emby-library/inspect', { params: { rcloneRemote, path } })
}

/** 返回汇总统计 */
export function getInspectSummary(rcloneRemote, path = '/') {
  return request.get('/emby-library/summary', { params: { rcloneRemote, path } })
}
