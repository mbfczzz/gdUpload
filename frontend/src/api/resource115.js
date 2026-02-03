import request from '@/utils/request'

/**
 * 智能搜索115资源
 */
export function smartSearch115(tmdbId, name, originalTitle, year) {
  return request({
    url: '/resource115/smart-search',
    method: 'get',
    params: {
      tmdbId,
      name,
      originalTitle,
      year
    }
  })
}

/**
 * 转存115资源
 */
export function transfer115(url, code) {
  return request({
    url: '/resource115/transfer',
    method: 'post',
    params: {
      url,
      code
    }
  })
}
