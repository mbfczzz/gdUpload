import request from '@/utils/request'

/**
 * 账号管理API
 */

// 分页查询账号列表
export function getAccountPage(params) {
  return request({
    url: '/account/page',
    method: 'get',
    params
  })
}

// 获取账号详情
export function getAccountById(id) {
  return request({
    url: `/account/${id}`,
    method: 'get'
  })
}

// 获取全部账号列表
export function getAccountList() {
  return request({
    url: '/account/list',
    method: 'get'
  })
}

// 获取可用账号列表
export function getAvailableAccounts() {
  return request({
    url: '/account/available',
    method: 'get'
  })
}

// 新增账号
export function addAccount(data) {
  return request({
    url: '/account',
    method: 'post',
    data
  })
}

// 更新账号
export function updateAccount(data) {
  return request({
    url: '/account',
    method: 'put',
    data
  })
}

// 删除账号
export function deleteAccount(id) {
  return request({
    url: `/account/${id}`,
    method: 'delete'
  })
}

// 批量删除账号
export function batchDeleteAccount(ids) {
  return request({
    url: '/account/batch',
    method: 'delete',
    data: ids
  })
}

// 启用/禁用账号
export function toggleAccountStatus(id, status) {
  return request({
    url: `/account/${id}/status`,
    method: 'put',
    params: { status }
  })
}

// 验证rclone配置
export function validateRclone(configName) {
  return request({
    url: '/account/validate-rclone',
    method: 'get',
    params: { configName }
  })
}

// 探测账号是否可用
export function probeAccount(id) {
  return request({
    url: `/account/${id}/probe`,
    method: 'post'
  })
}
