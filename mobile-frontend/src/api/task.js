import request from '@/utils/request'

// 获取任务列表
export const getTaskList = (params) => {
  return request.get('/task/page', { params })
}

// 启动任务
export const startTask = (id) => {
  return request.put(`/task/${id}/start`)
}

// 暂停任务
export const pauseTask = (id) => {
  return request.put(`/task/${id}/pause`)
}

// 取消任务
export const cancelTask = (id) => {
  return request.put(`/task/${id}/cancel`)
}

// 删除任务
export const deleteTask = (id) => {
  return request.delete(`/task/${id}`)
}

// 获取任务文件列表
export const getTaskFiles = (id) => {
  return request.get(`/task/${id}/files`)
}

// 修复文件路径
export const fixFilePath = (taskId) => {
  return request.post(`/fix/emby-file-info?taskId=${taskId}`)
}
