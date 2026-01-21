import request from '@/utils/request'

/**
 * 日志管理API
 */

// 分页查询日志
export function getLogPage(params) {
  return request({
    url: '/log/page',
    method: 'get',
    params
  })
}

// 清理过期日志
export function cleanExpiredLogs(days) {
  return request({
    url: '/log/clean',
    method: 'delete',
    params: { days }
  })
}
