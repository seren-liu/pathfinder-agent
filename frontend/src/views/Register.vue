<template>
  <div class="register-container">
    <div class="register-card">
      <div class="card-header">
        <el-icon class="header-icon" :size="48"><UserFilled /></el-icon>
        <h2 class="header-title">Create Your Account</h2>
        <p class="header-subtitle">Join us and start planning your dream trip</p>
      </div>

      <el-form
        ref="registerFormRef"
        :model="registerForm"
        :rules="registerRules"
        label-position="top"
        class="register-form"
      >
        <el-form-item label="Email" prop="email">
          <el-input
            v-model="registerForm.email"
            placeholder="alice@example.com"
            size="large"
            :prefix-icon="Message"
          />
        </el-form-item>

        <el-form-item label="Password" prop="password">
          <el-input
            v-model="registerForm.password"
            type="password"
            placeholder="At least 6 characters"
            size="large"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>

        <el-form-item label="Confirm Password" prop="confirmPassword">
          <el-input
            v-model="registerForm.confirmPassword"
            type="password"
            placeholder="Re-enter your password"
            size="large"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>

        <el-form-item>
          <el-checkbox v-model="agreeTerms">
            I agree to the <a href="#" class="link">Terms & Privacy Policy</a>
          </el-checkbox>
        </el-form-item>

        <el-button
          type="primary"
          size="large"
          class="submit-button"
          :loading="loading"
          :disabled="!agreeTerms"
          @click="handleRegister"
        >
          Register & Continue
        </el-button>
      </el-form>

      <div class="footer-text">
        Already have an account? 
        <router-link to="/login" class="link">Log In</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { UserFilled, Message, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

// ========== 表单数据 ==========
const registerFormRef = ref(null)
const registerForm = ref({
  email: '',
  password: '',
  confirmPassword: ''
})

const agreeTerms = ref(false)
const loading = ref(false)

// ========== 表单验证规则 ==========
const validateConfirmPassword = (rule, value, callback) => {
  if (value !== registerForm.value.password) {
    callback(new Error('Passwords do not match'))
  } else {
    callback()
  }
}

const registerRules = {
  email: [
    { required: true, message: 'Please enter your email', trigger: 'blur' },
    { type: 'email', message: 'Invalid email format', trigger: 'blur' }
  ],
  password: [
    { required: true, message: 'Please enter your password', trigger: 'blur' },
    { min: 6, message: 'Password must be at least 6 characters', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: 'Please confirm your password', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

// ========== 注册处理 ==========
const handleRegister = async () => {
  if (!registerFormRef.value) return

  // 验证表单
  const valid = await registerFormRef.value.validate().catch(() => false)
  if (!valid) return

  if (!agreeTerms.value) {
    ElMessage.warning('Please agree to the Terms & Privacy Policy')
    return
  }

  loading.value = true

  try {
    await userStore.register(registerForm.value.email, registerForm.value.password)
    
    ElMessage.success('Registration successful!')
    
    // 跳转到个人资料设置页面
    router.push('/onboarding/profile')
  } catch (error) {
    console.error('Registration failed:', error)
    ElMessage.error(error.response?.data?.message || 'Registration failed')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  padding: 40px 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.register-card {
  width: 100%;
  max-width: 500px;
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

.register-form {
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
  .register-card {
    padding: 32px 24px;
  }

  .header-title {
    font-size: 24px;
  }
}
</style>

