import request from '../utils/request'

/**
 * 获取所有配置
 */
export function getAllConfigs() {
  return request({
    url: '/emby/config/list',
    method: 'get'
  })
}

/**
 * 获取默认配置
 */
export function getDefaultConfig() {
  return request({
    url: '/emby/config/default',
    method: 'get'
  })
}

/**
 * 获取配置详情
 */
export function getConfig(id) {
  return request({
    url: `/emby/config/${id}`,
    method: 'get'
  })
}

/**
 * 保存或更新配置
 */
export function saveConfig(data) {
  return request({
    url: '/emby/config/save',
    method: 'post',
    data
  })
}

/**
 * 删除配置
 */
export function deleteConfig(id) {
  return request({
    url: `/emby/config/${id}`,
    method: 'delete'
  })
}

/**
 * 设置默认配置
 */
export function setDefaultConfig(id) {
  return request({
    url: `/emby/config/${id}/default`,
    method: 'put'
  })
}

/**
 * 测试配置
 */
export function testConfig(data) {
  return request({
    url: '/emby/config/test',
    method: 'post',
    data
  })
}

/**
 * 启用/禁用配置
 */
export function toggleConfig(id) {
  return request({
    url: `/emby/config/${id}/toggle`,
    method: 'put'
  })
}
