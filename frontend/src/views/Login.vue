<template>
  <div class="login-container">
    <div class="login-card">
      <div class="card-header">
        <el-icon class="header-icon" :size="48"><User /></el-icon>
        <h2 class="header-title">Welcome Back</h2>
        <p class="header-subtitle">Log in to continue your journey</p>
      </div>

      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        label-position="top"
        class="login-form"
      >
        <el-form-item label="Email" prop="email">
          <el-input
            v-model="loginForm.email"
            placeholder="alice@example.com"
            size="large"
            :prefix-icon="Message"
          />
        </el-form-item>

        <el-form-item label="Password" prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="Enter your password"
            size="large"
            :prefix-icon="Lock"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-button
          type="primary"
          size="large"
          class="submit-button"
          :loading="loading"
          @click="handleLogin"
        >
          Log In
        </el-button>
      </el-form>

      <div class="footer-text">
        Don't have an account? 
        <router-link to="/register" class="link">Register</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Message, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

// ========== 表单数据 ==========
const loginFormRef = ref(null)
const loginForm = ref({
  email: '',
  password: ''
})

const loading = ref(false)

// ========== 表单验证规则 ==========
const loginRules = {
  email: [
    { required: true, message: 'Please enter your email', trigger: 'blur' },
    { type: 'email', message: 'Invalid email format', trigger: 'blur' }
  ],
  password: [
    { required: true, message: 'Please enter your password', trigger: 'blur' }
  ]
}

// ========== 登录处理 ==========
const handleLogin = async () => {
  if (!loginFormRef.value) return

  // 验证表单
  const valid = await loginFormRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true

  try {
    const response = await userStore.login(loginForm.value.email, loginForm.value.password)
    
    ElMessage.success('Login successful!')
    
    // 根据是否已设置 profile 决定跳转路径
    if (response.hasProfile) {
      // 如果有 profile，跳转到意图输入页面
      const redirect = route.query.redirect || '/plan/intent'
      router.push(redirect)
    } else {
      // 如果没有 profile，跳转到设置页面
      router.push('/onboarding/profile')
    }
  } catch (error) {
    console.error('Login failed:', error)
    ElMessage.error(error.response?.data?.message || 'Login failed')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  padding: 40px 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-card {
  width: 100%;
  max-width: 460px;
  background: white;
  border-radius: 16px;
  padding: 48px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.card-header {
  text-align: center;
  margin-bottom: 36px;
}

.header-icon {
  color: #667eea;
  margin-bottom: 16px;
}

.header-title {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
  margin-bottom: 8px;
}

.header-subtitle {
  font-size: 15px;
  color: #909399;
}

.login-form {
  margin-top: 24px;
}

.submit-button {
  width: 100%;
  margin-top: 12px;
  font-weight: 600;
}

.footer-text {
  text-align: center;
  margin-top: 24px;
  font-size: 14px;
  color: #606266;
}

.link {
  color: #667eea;
  text-decoration: none;
  font-weight: 500;
}

.link:hover {
  text-decoration: underline;
}

/* 响应式 */
@media (max-width: 768px) {
  .login-card {
    padding: 32px 24px;
  }

  .header-title {
    font-size: 24px;
  }
}
</style>

