import request from '@/utils/request'

/**
 * 订阅批量任务API
 */

// 创建批量任务
export function createBatchTask(data) {
  return request({
    url: '/subscribe-batch/create',
    method: 'post',
    data
  })
}

// 启动任务
export function startTask(taskId) {
  return request({
    url: `/subscribe-batch/start/${taskId}`,
    method: 'post'
  })
}

// 暂停任务
export function pauseTask(taskId) {
  return request({
    url: `/subscribe-batch/pause/${taskId}`,
    method: 'post'
  })
}

// 获取任务详情
export function getTaskDetail(taskId) {
  return request({
    url: `/subscribe-batch/${taskId}`,
    method: 'get'
  })
}

// 分页查询任务列表
export function getTaskPage(params) {
  return request({
    url: '/subscribe-batch/page',
    method: 'get',
    params
  })
}

// 获取任务日志
export function getTaskLogs(taskId) {
  return request({
    url: `/subscribe-batch/${taskId}/logs`,
    method: 'get'
  })
}

// 删除任务
export function deleteTask(taskId) {
  return request({
    url: `/subscribe-batch/${taskId}`,
    method: 'delete'
  })
}

// 获取任务统计
export function getTaskStatistics() {
  return request({
    url: '/subscribe-batch/statistics',
    method: 'get'
  })
}
