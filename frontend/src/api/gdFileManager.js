import request from '@/utils/request'

/**
 * GD文件管理API
 */

// 列出目录内容（服务端分页）
export function listFiles(accountId, path = '', page = 1, size = 50) {
  return request({
    url: '/gd-file/list',
    method: 'get',
    params: { accountId, path, page, size }
  })
}

// 删除文件
export function deleteFile(accountId, filePath) {
  return request({
    url: '/gd-file/file',
    method: 'delete',
    data: { accountId, filePath }
  })
}

// 删除目录
export function deleteDir(accountId, dirPath) {
  return request({
    url: '/gd-file/dir',
    method: 'delete',
    data: { accountId, dirPath }
  })
}

// 移动/重命名
export function moveItem(accountId, oldPath, newPath, isDir) {
  return request({
    url: '/gd-file/move',
    method: 'put',
    data: { accountId, oldPath, newPath, isDir }
  })
}

// 创建目录
export function mkdir(accountId, path) {
  return request({
    url: '/gd-file/mkdir',
    method: 'post',
    data: { accountId, path }
  })
}

// 启动批量格式化命名任务
export function startBatchFormat(accountId, dirPath) {
  return request({
    url: '/gd-file/batch-format/start',
    method: 'post',
    data: { accountId, dirPath }
  })
}

// 查询批量格式化命名任务进度
export function getBatchFormatStatus(taskId) {
  return request({
    url: `/gd-file/batch-format/${taskId}/status`,
    method: 'get'
  })
}

// 取消批量格式化命名任务
export function cancelBatchFormat(taskId) {
  return request({
    url: `/gd-file/batch-format/${taskId}`,
    method: 'delete'
  })
}
