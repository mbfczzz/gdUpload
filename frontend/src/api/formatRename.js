import request from '@/utils/request'

export function startFormatRenameTask(accountId, dirPath) {
  return request({ url: '/format-rename/start', method: 'post', data: { accountId, dirPath } })
}

export function getFormatRenameTasks(page, size) {
  return request({ url: '/format-rename/list', method: 'get', params: { page, size } })
}

export function getFormatRenameTask(id) {
  return request({ url: `/format-rename/${id}`, method: 'get' })
}

export function getFormatRenameHistory(id, page, size, status) {
  return request({ url: `/format-rename/${id}/history`, method: 'get', params: { page, size, status } })
}

export function cancelFormatRenameTask(id) {
  return request({ url: `/format-rename/${id}`, method: 'delete' })
}

export function pauseFormatRenameTask(id) {
  return request({ url: `/format-rename/${id}/pause`, method: 'post' })
}

export function resumeFormatRenameTask(id) {
  return request({ url: `/format-rename/${id}/resume`, method: 'post' })
}
