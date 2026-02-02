import axios from 'axios'
import { ElMessage } from 'element-plus'
import request from '../utils/request'

// 创建独立的axios实例用于订阅搜索API
const subscribeRequest = axios.create({
  baseURL: 'http://104.251.122.51:3000/api/v1',
  timeout: 30000,
  headers: {
    'Accept': 'application/json, text/plain, */*',
    'Accept-Language': 'zh-CN,zh;q=0.9',
    'Authorization': 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3Njk4NjQxNDEsImlhdCI6MTc2OTE3Mjk0MSwic3ViIjoiMSIsInVzZXJuYW1lIjoiYWRtaW4iLCJzdXBlcl91c2VyIjp0cnVlLCJsZXZlbCI6MiwicHVycG9zZSI6ImF1dGhlbnRpY2F0aW9uIn0.Iy35JG05DpMuHeMCIzhXV6uFRBkONhcQw3_bA5IFU4Q'
  }
})

// 创建独立的axios实例用于Telegram搜索API
const telegramSearchRequest = axios.create({
  baseURL: 'http://104.251.122.51:8095/api/v1',
  timeout: 30000,
  headers: {
    'Accept': 'application/json, text/plain, */*',
    'Accept-Language': 'zh-CN,zh;q=0.9',
    'Authorization': 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3NzE3ODQxMTEsInVzZXJuYW1lIjoiYWRtaW4ifQ.9EPowlYRQCLx1p03TfbAQ9T8cxKUQdSrBVEQa67R1nI',
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36'
  }
})

// 响应拦截器
subscribeRequest.interceptors.response.use(
  response => {
    return response.data
  },
  error => {
    console.error('订阅搜索API错误:', error)

    let message = '网络错误'
    if (error.response) {
      switch (error.response.status) {
        case 400:
          message = '请求参数错误'
          break
        case 401:
          message = '未授权，请检查Token'
          break
        case 403:
          message = '拒绝访问'
          break
        case 404:
          message = '未找到订阅信息'
          break
        case 500:
          message = '服务器错误'
          break
        default:
          message = error.response.data.message || '请求失败'
      }
    }

    ElMessage.error(message)
    return Promise.reject(error)
  }
)

// Telegram搜索响应拦截器
telegramSearchRequest.interceptors.response.use(
  response => {
    return response.data
  },
  error => {
    console.error('Telegram搜索API错误:', error)

    let message = '网络错误'
    if (error.response) {
      switch (error.response.status) {
        case 400:
          message = '请求参数错误'
          break
        case 401:
          message = '未授权，请检查Token'
          break
        case 403:
          message = '拒绝访问'
          break
        case 404:
          message = '未找到搜索结果'
          break
        case 500:
          message = '服务器错误'
          break
        default:
          message = error.response.data.message || '请求失败'
      }
    }

    ElMessage.error(message)
    return Promise.reject(error)
  }
)

/**
 * 订阅搜索API
 */

// 搜索订阅（通过ID）
export function searchSubscribe(id) {
  return subscribeRequest({
    url: `/subscribe/search/${id}`,
    method: 'get'
  })
}

// 通过关键词搜索（Telegram搜索）
export function searchByKeyword(keyword, force = true) {
  return telegramSearchRequest({
    url: '/telegramsearch/search',
    method: 'get',
    params: {
      keyword,
      force
    }
  })
}

// 转存到阿里云盘
export function transferToAlipan(url, parentId = '697f2333cd2704159fa446d8bc5077584838e3dc', cloudType = 'channel_alipan') {
  return telegramSearchRequest({
    url: '/telegramsearch/transfer',
    method: 'post',
    data: {
      url: url,
      parent_id: parentId,
      cloud_type: cloudType
    }
  })
}

// 验证链接有效性
export function validateLink(url) {
  return telegramSearchRequest({
    url: '/telegramsearch/validate',
    method: 'post',
    data: {
      url: url
    }
  })
}

// 批量验证链接有效性（本地后端）
export function batchValidateLinks(urls) {
  return request({
    url: '/telegramsearch/batch-validate',
    method: 'post',
    data: {
      urls: urls
    }
  })
}

// AI智能筛选最佳资源（本地后端）
export function aiSelectBestResource(movieInfo, resources) {
  return request({
    url: '/telegramsearch/ai-select',
    method: 'post',
    data: {
      movie_info: movieInfo,
      resources: resources
    }
  })
}

export default subscribeRequest
