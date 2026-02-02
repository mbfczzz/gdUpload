import request from '../utils/request'

/**
 * 获取完整配置
 */
export function getFullConfig() {
  return request({
    url: '/smart-search-config/full',
    method: 'get'
  })
}

/**
 * 保存完整配置
 */
export function saveFullConfig(configData) {
  return request({
    url: '/smart-search-config/full',
    method: 'post',
    data: configData
  })
}

/**
 * 获取所有配置
 */
export function getAllConfigs() {
  return request({
    url: '/smart-search-config/list',
    method: 'get'
  })
}

/**
 * 根据类型获取配置
 */
export function getConfigsByType(configType) {
  return request({
    url: `/smart-search-config/type/${configType}`,
    method: 'get'
  })
}

/**
 * 保存单个配置
 */
export function saveConfig(config) {
  return request({
    url: '/smart-search-config/save',
    method: 'post',
    data: config
  })
}

/**
 * 删除配置
 */
export function deleteConfig(id) {
  return request({
    url: `/smart-search-config/${id}`,
    method: 'delete'
  })
}
