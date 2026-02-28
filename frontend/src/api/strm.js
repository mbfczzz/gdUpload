import request from '@/utils/request'

/**
 * STRM 生成 API
 */

/** 启动 STRM 生成任务 */
export function generateStrm(gdRemote, gdSourcePath) {
  return request.post('/strm/generate', { gdRemote, gdSourcePath })
}

/** 查询当前任务状态 */
export function getStrmStatus() {
  return request.get('/strm/status')
}
