import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { registerApi, loginApi, logoutApi } from '@/api/auth'
import { setupProfileApi, getUserDetailApi } from '@/api/user'

export const useUserStore = defineStore('user', () => {

  const user = ref(null)
  const token = ref(localStorage.getItem('token') || '')
  const userId = ref(localStorage.getItem('userId') ? parseInt(localStorage.getItem('userId')) : null)
  
  const isLoggedIn = computed(() => !!token.value && !!userId.value)
  const hasProfile = computed(() => user.value?.location !== null && user.value?.travelStyle !== null)
  
  /**
   * 设置用户信息
   */
  const setUser = (userData) => {
    user.value = userData
  }
  
  /**
   * 设置 Token
   */
  const setToken = (newToken) => {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }
  
  /**
   * 设置用户 ID
   */
  const setUserId = (id) => {
    userId.value = id
    localStorage.setItem('userId', id.toString())
  }
  
  /**
   * 清除用户数据
   */
  const clearUser = () => {
    user.value = null
    token.value = ''
    userId.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('userId')
  }
  
  /**
   * 用户注册
   */
  const register = async (email, password) => {
    try {
      const response = await registerApi({ email, password })
      
      setToken(response.token)
      setUserId(response.userId)
      setUser({
        userId: response.userId,
        email: response.email,
        status: response.status
      })
      
      return response
    } catch (error) {
      throw error
    }
  }
  
  /**
   * 用户登录
   */
  const login = async (email, password) => {
    try {
      const response = await loginApi({ email, password })
      
      setToken(response.token)
      setUserId(response.userId)
      setUser({
        userId: response.userId,
        email: response.email,
        status: response.status
      })
      
      // 如果已有 profile，自动获取完整信息
      if (response.hasProfile) {
        await fetchUserDetail()
      }
      
      return response
    } catch (error) {
      throw error
    }
  }
  
  /**
   * 用户退出
   */
  const logout = async () => {
    try {
      await logoutApi()
    } catch (error) {
      console.error('Logout API error:', error)
    } finally {
      clearUser()
    }
  }
  
  /**
   * 设置用户个人资料
   */
  const setupProfile = async (profileData) => {
    if (!userId.value) {
      throw new Error('User not logged in')
    }
    
    try {
      const response = await setupProfileApi(userId.value, profileData)
      setUser(response)
      return response
    } catch (error) {
      throw error
    }
  }
  
  /**
   * 获取用户详情
   */
  const fetchUserDetail = async () => {
    if (!userId.value) {
      throw new Error('User not logged in')
    }
    
    try {
      const response = await getUserDetailApi(userId.value)
      setUser(response)
      return response
    } catch (error) {
      throw error
    }
  }
  
  // ========== Return ==========
  return {
    // State
    user,
    token,
    userId,
    
    // Getters
    isLoggedIn,
    hasProfile,
    
    // Actions
    setUser,
    setToken,
    setUserId,
    clearUser,
    register,
    login,
    logout,
    setupProfile,
    fetchUserDetail
  }
})

