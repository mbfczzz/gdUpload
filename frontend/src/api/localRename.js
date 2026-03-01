import request from '@/utils/request'

export const startLocalRename = (dirPath) =>
    request.post('/local-rename/start', { dirPath })

export const getLocalRenameStatus = (taskId) =>
    request.get(`/local-rename/status/${taskId}`)

export const cancelLocalRename = (taskId) =>
    request.post(`/local-rename/cancel/${taskId}`)
