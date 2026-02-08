import request from '../utils/request'

/**
 * 通过TMDB ID转存115资源
 */
export function transferByTmdbId(tmdbId, targetFolderId = null) {
  return request({
    url: '/115-transfer/by-tmdb-id',
    method: 'post',
    data: {
      tmdbId,
      targetFolderId
    }
  })
}

/**
 * 直接转存115资源
 */
export function transfer115Resource(resource) {
  return request({
    url: '/115-transfer/transfer',
    method: 'post',
    data: resource
  })
}

/**
 * 测试115 Cookie
 */
export function test115Cookie() {
  return request({
    url: '/115-transfer/test-cookie',
    method: 'get'
  })
}

/**
 * 获取115用户信息
 */
export function get115UserInfo() {
  return request({
    url: '/115-transfer/user-info',
    method: 'get'
  })
}
