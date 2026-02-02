import axios from 'axios'
import { ElMessage } from 'element-plus'

// 创建 axios 实例
// baseURL 配置说明：
// - 开发环境：使用 '/api'，由 Vite 代理到 http://localhost:8080
// - 生产环境：使用 '/api'，由 Nginx 代理到后端服务器
// - 如果设置了 VITE_API_BASE_URL 环境变量，则使用该值
// 注意：所有 API 调用的 URL 中不应该包含 '/api' 前缀，因为 baseURL 已经包含了
const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000, // 30 秒超时
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
service.interceptors.request.use(
  config => {
    // 从 localStorage 获取 token
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  error => {
    console.error('Request error:', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  response => {
    const res = response.data
    
    // 如果没有code字段，可能是直接返回数据，直接返回
    if (res.code === undefined) {
      return res
    }
    
    // 如果返回的状态码不是 200，则认为是错误
    if (res.code !== undefined && res.code !== 200) {
      // 401: 未授权，跳转到登录页
      if (res.code === 401) {
        localStorage.removeItem('token')
        window.location.href = '/login'
        return Promise.reject(new Error(res.message || 'Unauthorized'))
      }
      
      // 返回响应数据，让业务层处理错误（不要 reject，让业务层判断）
      return res
    }
    
    // 成功时返回data字段（实际数据），而不是整个CommonResponse
    return res.data || res
  },
  error => {
    console.error('Response error:', error)
    
    let message = 'Network error'
    
    if (error.response) {
      switch (error.response.status) {
        case 400:
          message = 'Bad request'
          break
        case 401:
          message = 'Unauthorized'
          localStorage.removeItem('token')
          window.location.href = '/login'
          break
        case 403:
          message = 'Forbidden'
          break
        case 404:
          message = 'Not found'
          break
        case 500:
          message = 'Server error'
          break
        default:
          message = error.response.data?.message || 'Unknown error'
      }
    } else if (error.request) {
      message = 'No response from server'
    }
    
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default service
