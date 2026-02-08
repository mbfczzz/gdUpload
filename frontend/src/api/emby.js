import request from '../utils/request'

/**
 * 测试Emby连接
 */
export function testEmbyConnection() {
  return request({
    url: '/emby/test',
    method: 'get'
  })
}

/**
 * 获取Emby服务器信息
 */
export function getEmbyServerInfo() {
  return request({
    url: '/emby/server-info',
    method: 'get'
  })
}

/**
 * 获取所有媒体库
 */
export function getAllLibraries() {
  return request({
    url: '/emby/libraries',
    method: 'get'
  })
}

/**
 * 获取指定媒体库的媒体项
 */
export function getLibraryItems(libraryId, params) {
  return request({
    url: `/emby/libraries/${libraryId}/items`,
    method: 'get',
    params
  })
}

/**
 * 获取指定媒体库的媒体项（分页）
 */
export function getLibraryItemsPaged(libraryId, startIndex = 0, limit = 50, transferStatus = null, downloadStatus = null) {
  const params = { startIndex, limit }
  if (transferStatus) {
    params.transferStatus = transferStatus
  }
  if (downloadStatus) {
    params.downloadStatus = downloadStatus
  }
  return request({
    url: `/emby/libraries/${libraryId}/items/paged`,
    method: 'get',
    params
  })
}

/**
 * 获取媒体项详情
 */
export function getItemDetail(itemId) {
  return request({
    url: `/emby/items/${itemId}`,
    method: 'get'
  })
}

/**
 * 获取电视剧的所有剧集
 */
export function getSeriesEpisodes(seriesId) {
  return request({
    url: `/emby/series/${seriesId}/episodes`,
    method: 'get'
  })
}

/**
 * 获取所有类型
 */
export function getAllGenres() {
  return request({
    url: '/emby/genres',
    method: 'get'
  })
}

/**
 * 获取所有标签
 */
export function getAllTags() {
  return request({
    url: '/emby/tags',
    method: 'get'
  })
}

/**
 * 获取所有工作室
 */
export function getAllStudios() {
  return request({
    url: '/emby/studios',
    method: 'get'
  })
}

/**
 * 搜索媒体项
 */
export function searchItems(keyword) {
  return request({
    url: '/emby/search',
    method: 'get',
    params: { keyword }
  })
}

/**
 * 同步所有媒体库数据
 */
export function syncAllLibraries() {
  return request({
    url: '/emby/sync',
    method: 'post'
  })
}

/**
 * 清空缓存
 */
export function clearCache() {
  return request({
    url: '/emby/cache/clear',
    method: 'post'
  })
}

/**
 * 获取缓存状态
 */
export function getCacheStatus() {
  return request({
    url: '/emby/cache/status',
    method: 'get'
  })
}

/**
 * 获取媒体项的下载URL
 */
export function getDownloadUrls(itemId) {
  return request({
    url: `/emby/items/${itemId}/download-urls`,
    method: 'get'
  })
}

/**
 * 下载媒体项到服务器
 */
export function downloadToServer(itemId) {
  return request({
    url: `/emby/items/${itemId}/download-to-server`,
    method: 'post'
  })
}

/**
 * 批量下载媒体项到服务器（后端队列执行）
 */
export function batchDownloadToServer(itemIds) {
  return request({
    url: '/emby/batch-download-to-server',
    method: 'post',
    data: itemIds
  })
}

/**
 * 获取批量下载进度
 */
export function getBatchDownloadProgress() {
  return request({
    url: '/emby/batch-download-progress',
    method: 'get'
  })
}
