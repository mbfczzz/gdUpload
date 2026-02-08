import request from '../utils/request'

/**
 * 批量检查媒体项的下载状态
 */
export function batchCheckDownloadStatus(embyItemIds) {
  return request({
    url: '/emby-download-history/batch-check',
    method: 'post',
    data: embyItemIds
  })
}

/**
 * 根据Emby媒体项ID获取下载历史
 */
export function getDownloadHistoryByEmbyItemId(embyItemId) {
  return request({
    url: `/emby-download-history/item/${embyItemId}`,
    method: 'get'
  })
}

/**
 * 手动标记下载状态
 */
export function markDownloadStatus(embyItemId, status) {
  return request({
    url: '/emby-download-history/mark-status',
    method: 'post',
    params: {
      embyItemId,
      status
    }
  })
}
