<!-- src/App.vue -->
<template>
  <router-view />
</template>

<script setup>
import { onMounted } from 'vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

// 应用启动时，如果用户已登录，自动恢复用户信息
onMounted(async () => {
  if (userStore.isLoggedIn && !userStore.user) {
    try {
      await userStore.fetchUserDetail()
      console.log('User info restored on app mount')
    } catch (error) {
      console.error('Failed to restore user info:', error)
      // 如果获取失败（比如 token 过期），清除登录状态
      if (error.response?.status === 401) {
        userStore.clearUser()
      }
    }
  }
})
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

#app {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Arial, sans-serif;
}
</style>