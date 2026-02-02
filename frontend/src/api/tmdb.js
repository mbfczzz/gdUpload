import request from '@/utils/request'

/**
 * 搜索TMDB ID
 */
export function searchTmdbId(name, year, type) {
  return request({
    url: '/tmdb/search',
    method: 'get',
    params: {
      name,
      year,
      type
    }
  })
}

/**
 * 批量补充115资源的TMDB ID
 */
export function batchFillTmdbIds() {
  return request({
    url: '/tmdb/batch-fill',
    method: 'post'
  })
}
