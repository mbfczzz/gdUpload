import request from '../utils/request'

/**
 * 保存转存记录
 */
export function saveTransferHistory(history) {
  return request({
    url: '/transfer-history/save',
    method: 'post',
    data: history
  })
}

/**
 * 根据Emby媒体项ID获取转存历史
 */
export function getHistoryByEmbyItemId(embyItemId) {
  return request({
    url: `/transfer-history/item/${embyItemId}`,
    method: 'get'
  })
}

/**
 * 检查媒体项是否已成功转存
 */
export function hasSuccessfulTransfer(embyItemId) {
  return request({
    url: `/transfer-history/check/${embyItemId}`,
    method: 'get'
  })
}

/**
 * 批量检查媒体项的转存状态
 */
export function batchCheckTransferStatus(embyItemIds) {
  return request({
    url: '/transfer-history/batch-check',
    method: 'post',
    data: embyItemIds
  })
}

/**
 * 获取最近的转存记录
 */
export function getRecentHistory(limit = 50) {
  return request({
    url: '/transfer-history/recent',
    method: 'get',
    params: { limit }
  })
}

/**
 * 获取转存统计信息
 */
export function getTransferStatistics() {
  return request({
    url: '/transfer-history/statistics',
    method: 'get'
  })
}
