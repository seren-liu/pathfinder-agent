import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

// 创建 axios 实例
// baseURL 配置说明：
// - 开发环境：使用 '/api'，由 Vite 代理到 http://localhost:8080
// - 生产环境：使用 '/api'，由 Nginx 代理到后端服务器
// - 如果设置了 VITE_API_BASE_URL 环境变量，则使用该值
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 60000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
request.interceptors.request.use(
  config => {
    const userStore = useUserStore()
    
    // 添加 token 到请求头
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    
    // 添加 userId 到请求头（简化版认证）
    if (userStore.userId) {
      config.headers['X-User-Id'] = userStore.userId
    }
    
    return config
  },
  error => {
    console.error('Request error:', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  response => {
    const res = response.data

    // 统一契约：
    // 1) CommonResponse(code/data/message) -> code=200 返回 data，其余 reject
    // 2) 非 CommonResponse（无 code） -> 直接返回原始 data
    if (res && typeof res === 'object' && Object.prototype.hasOwnProperty.call(res, 'code')) {
      if (res.code === 200) {
        return res.data
      }

      const message = res.message || 'Request failed'
      ElMessage.error(message)
      const error = new Error(message)
      error.name = 'ApiBusinessError'
      error.response = response
      error.code = res.code
      return Promise.reject(error)
    }

    return res
  },
  error => {
    console.error('Response error:', error)
    
    // 处理不同的错误状态码
    if (error.response) {
      const { status, data } = error.response
      
      switch (status) {
        case 400:
          ElMessage.error(data.message || 'Bad Request')
          break
        case 401:
          ElMessage.error('Unauthorized, please login')
          try {
            const userStore = useUserStore()
            userStore.clearUser()
          } catch (e) {
            localStorage.removeItem('token')
            localStorage.removeItem('userId')
          }
          if (window.location.pathname !== '/login') {
            window.location.href = '/login'
          }
          break
        case 404:
          ElMessage.error('Resource not found')
          break
        case 500:
          ElMessage.error('Server error')
          break
        default:
          ElMessage.error(data.message || 'Unknown error')
      }
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('Request timeout')
    } else {
      ElMessage.error('Network error')
    }
    
    return Promise.reject(error)
  }
)

export default request
