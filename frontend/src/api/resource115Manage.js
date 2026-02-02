import request from '@/utils/request'

/**
 * 分页查询115资源
 */
export function getResource115Page(page, size, keyword) {
  return request({
    url: '/resource115/manage/page',
    method: 'get',
    params: {
      page,
      size,
      keyword
    }
  })
}

/**
 * 根据ID获取115资源
 */
export function getResource115ById(id) {
  return request({
    url: `/resource115/manage/${id}`,
    method: 'get'
  })
}

/**
 * 添加115资源
 */
export function addResource115(data) {
  return request({
    url: '/resource115/manage',
    method: 'post',
    data
  })
}

/**
 * 更新115资源
 */
export function updateResource115(data) {
  return request({
    url: '/resource115/manage',
    method: 'put',
    data
  })
}

/**
 * 删除115资源
 */
export function deleteResource115(id) {
  return request({
    url: `/resource115/manage/${id}`,
    method: 'delete'
  })
}

/**
 * 批量删除115资源
 */
export function batchDeleteResource115(ids) {
  return request({
    url: '/resource115/manage/batch',
    method: 'delete',
    data: ids
  })
}
