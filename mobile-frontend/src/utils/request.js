import axios from 'axios'
import { showToast } from 'vant'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

request.interceptors.response.use(
  response => {
    const { data } = response
    if (data.code !== 200) {
      showToast(data.message || '请求失败')
      return Promise.reject(new Error(data.message))
    }
    return response
  },
  error => {
    showToast(error.message || '网络错误')
    return Promise.reject(error)
  }
)

export default request
