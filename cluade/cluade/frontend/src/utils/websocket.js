import SockJS from 'sockjs-client'
import Stomp from 'stompjs'

/**
 * WebSocket工具类
 */
class WebSocketClient {
  constructor() {
    this.stompClient = null
    this.connected = false
    this.subscriptions = new Map()
    this.reconnectTimer = null
    this.reconnectAttempts = 0
    this.maxReconnectAttempts = 5
  }

  /**
   * 连接WebSocket
   */
  connect(url) {
    return new Promise((resolve, reject) => {
      try {
        // 如果没有提供URL，使用默认配置
        if (!url) {
          // 开发环境使用相对路径，生产环境使用完整URL
          if (import.meta.env.DEV) {
            url = '/ws'
          } else {
            // 生产环境：使用当前页面的协议和主机，加上 /api 前缀
            const protocol = window.location.protocol === 'https:' ? 'https:' : 'http:'
            const host = window.location.host
            url = `${protocol}//${host}/api/ws`
          }
        }

        console.log('连接WebSocket:', url)
        const socket = new SockJS(url)
        this.stompClient = Stomp.over(socket)

        // 禁用调试日志
        this.stompClient.debug = null

        this.stompClient.connect(
          {},
          (frame) => {
            console.log('WebSocket连接成功:', frame)
            this.connected = true
            this.reconnectAttempts = 0
            resolve(frame)
          },
          (error) => {
            console.error('WebSocket连接失败:', error)
            this.connected = false
            this.handleReconnect()
            reject(error)
          }
        )
      } catch (error) {
        console.error('WebSocket初始化失败:', error)
        reject(error)
      }
    })
  }

  /**
   * 断开连接
   */
  disconnect() {
    if (this.stompClient && this.connected) {
      this.stompClient.disconnect(() => {
        console.log('WebSocket已断开')
        this.connected = false
      })
    }

    // 清除所有订阅
    this.subscriptions.clear()

    // 清除重连定时器
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
  }

  /**
   * 订阅主题
   * @param {string} destination - 订阅地址，如 '/topic/task/123'
   * @param {function} callback - 回调函数
   * @returns {string} 订阅ID
   */
  subscribe(destination, callback) {
    if (!this.connected || !this.stompClient) {
      console.warn('WebSocket未连接，无法订阅:', destination)
      return null
    }

    const subscription = this.stompClient.subscribe(destination, (message) => {
      try {
        const data = JSON.parse(message.body)
        callback(data)
      } catch (error) {
        console.error('解析WebSocket消息失败:', error)
      }
    })

    const subscriptionId = subscription.id
    this.subscriptions.set(subscriptionId, subscription)
    console.log('订阅成功:', destination, subscriptionId)

    return subscriptionId
  }

  /**
   * 取消订阅
   * @param {string} subscriptionId - 订阅ID
   */
  unsubscribe(subscriptionId) {
    const subscription = this.subscriptions.get(subscriptionId)
    if (subscription) {
      subscription.unsubscribe()
      this.subscriptions.delete(subscriptionId)
      console.log('取消订阅:', subscriptionId)
    }
  }

  /**
   * 发送消息
   * @param {string} destination - 目标地址
   * @param {object} body - 消息体
   */
  send(destination, body) {
    if (!this.connected || !this.stompClient) {
      console.warn('WebSocket未连接，无法发送消息')
      return
    }

    this.stompClient.send(destination, {}, JSON.stringify(body))
  }

  /**
   * 处理重连
   */
  handleReconnect() {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.error('WebSocket重连次数已达上限')
      return
    }

    this.reconnectAttempts++
    const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 30000)

    console.log(`WebSocket将在 ${delay}ms 后尝试第 ${this.reconnectAttempts} 次重连`)

    this.reconnectTimer = setTimeout(() => {
      console.log('尝试重新连接WebSocket...')
      this.connect().catch(() => {
        // 重连失败会自动触发handleReconnect
      })
    }, delay)
  }

  /**
   * 检查连接状态
   */
  isConnected() {
    return this.connected
  }
}

// 创建单例
const websocketClient = new WebSocketClient()

export default websocketClient
