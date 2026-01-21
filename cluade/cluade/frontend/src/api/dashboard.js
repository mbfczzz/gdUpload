import request from '@/utils/request'

/**
 * 数据统计API
 */

// 获取统计数据
export function getStats() {
  return request({
    url: '/dashboard/stats',
    method: 'get'
  })
}
