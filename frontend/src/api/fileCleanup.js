import request from '@/utils/request'

/**
 * 清理已上传文件的物理文件
 */
export function cleanupUploadedFiles() {
  return request({
    url: '/file-cleanup/cleanup',
    method: 'post'
  })
}
