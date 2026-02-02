<template>
  <AppLayout>
    <div class="profile-setup-container">
      <div class="setup-card">
        <!-- Progress Indicator -->
        <div class="progress-header">
          <div class="progress-bar">
            <div class="progress-fill" :style="{ width: `${progress}%` }"></div>
          </div>
          <p class="progress-text">Step 2 of 2: Complete Your Profile</p>
        </div>

        <div class="card-header">
          <el-icon class="header-icon" :size="48"><Compass /></el-icon>
          <h2 class="header-title">Tell Us About Yourself</h2>
          <p class="header-subtitle">Help us personalize your travel experience</p>
        </div>

        <el-form
          ref="profileFormRef"
          :model="profileForm"
          :rules="profileRules"
          label-position="top"
          class="profile-form"
        >
          <!-- Current Location -->
          <el-form-item label="Current Location" prop="location">
            <el-input
              v-model="profileForm.location"
              placeholder="Sydney, NSW"
              size="large"
              :prefix-icon="Location"
            >
              <template #append>
                <el-button 
                  :icon="LocationFilled" 
                  @click="detectLocation"
                  :loading="detectingLocation"
                  title="Detect my location"
                >
                  Detect
                </el-button>
              </template>
            </el-input>
            <span class="hint-text">
              {{ locationHint }}
            </span>
          </el-form-item>

          <!-- Age Range -->
          <el-form-item label="Age Range" prop="ageRange">
            <el-select
              v-model="profileForm.ageRange"
              placeholder="Select your age range"
              size="large"
              class="full-width"
            >
              <el-option label="18-30" value="18-30" />
              <el-option label="31-50" value="31-50" />
              <el-option label="51-65" value="51-65" />
              <el-option label="65+" value="65+" />
            </el-select>
          </el-form-item>

          <!-- Travel Style -->
          <el-form-item label="Travel Style (Select one)" prop="travelStyle">
            <div class="button-group">
              <el-button
                v-for="style in travelStyles"
                :key="style.value"
                :type="profileForm.travelStyle === style.value ? 'primary' : ''"
                size="large"
                @click="profileForm.travelStyle = style.value"
              >
                <el-icon class="button-icon"><component :is="style.icon" /></el-icon>
                {{ style.label }}
              </el-button>
            </div>
          </el-form-item>

          <!-- Travel Interests -->
          <el-form-item label="Travel Interests (Select multiple)" prop="interests">
            <div class="checkbox-group">
              <el-checkbox
                v-for="interest in interestOptions"
                :key="interest.value"
                v-model="selectedInterests[interest.value]"
                :label="interest.label"
                size="large"
                border
                @change="updateInterests"
              />
            </div>
            <span v-if="showInterestError" class="error-text">
              Please select at least one interest
            </span>
          </el-form-item>

          <!-- Budget Preference -->
          <el-form-item label="Budget Preference" prop="budgetPreference">
            <el-radio-group v-model="profileForm.budgetPreference" size="large">
              <el-radio :label="1">Budget ($)</el-radio>
              <el-radio :label="2">Moderate ($$)</el-radio>
              <el-radio :label="3">Luxury ($$$)</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-button
            type="primary"
            size="large"
            class="submit-button"
            :loading="loading"
            @click="handleSubmit"
          >
            <el-icon class="button-icon"><Check /></el-icon>
            Save & Start Planning
          </el-button>
        </el-form>
      </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Compass,
  Location,
  LocationFilled,
  User,
  UserFilled,
  Connection,
  Suitcase,
  Check
} from '@element-plus/icons-vue'
import AppLayout from '@/layouts/AppLayout.vue'
import { useUserStore } from '@/stores/user'
import { getLocationAddress, isGeolocationSupported } from '@/utils/geolocation'

const router = useRouter()
const userStore = useUserStore()

// ========== 表单数据 ==========
const profileFormRef = ref(null)
const profileForm = reactive({
  language: 'en',
  location: 'Sydney',
  ageRange: '',
  travelStyle: '',
  interests: [],
  budgetPreference: 2
})

const loading = ref(false)
const detectingLocation = ref(false)
const locationHint = ref('💡 Click "Detect" to auto-detect your location, or type manually')
const showInterestError = ref(false)
const progress = computed(() => 100) // 第2步，进度100%

// ========== 旅行风格选项 ==========
const travelStyles = [
  { label: 'Solo', value: 'solo', icon: User },
  { label: 'Family', value: 'family', icon: UserFilled },
  { label: 'Couple', value: 'couple', icon: Connection },
  { label: 'Business', value: 'business', icon: Suitcase }
]

// ========== 兴趣选项 ==========
const interestOptions = [
  { label: 'Beach', value: 'beach' },
  { label: 'Hiking', value: 'hiking' },
  { label: 'Culture', value: 'culture' },
  { label: 'Food', value: 'food' },
  { label: 'Photography', value: 'photography' },
  { label: 'Wildlife', value: 'wildlife' },
  { label: 'Relaxation', value: 'relaxation' }
]

const selectedInterests = reactive({})
interestOptions.forEach(option => {
  selectedInterests[option.value] = false
})

// ========== 更新兴趣数组 ==========
const updateInterests = () => {
  profileForm.interests = Object.keys(selectedInterests)
    .filter(key => selectedInterests[key])
  
  // 如果已经选择了至少一个兴趣，隐藏错误提示
  if (profileForm.interests.length > 0) {
    showInterestError.value = false
  }
}

// ========== 表单验证规则 ==========
const profileRules = {
  location: [
    { required: true, message: 'Please enter your location', trigger: 'blur' }
  ],
  ageRange: [
    { required: true, message: 'Please select your age range', trigger: 'change' }
  ],
  travelStyle: [
    { required: true, message: 'Please select a travel style', trigger: 'change' }
  ],
  interests: [
    {
      validator: (rule, value, callback) => {
        if (value.length === 0) {
          callback(new Error('Please select at least one interest'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
  budgetPreference: [
    { required: true, message: 'Please select budget preference', trigger: 'change' }
  ]
}

// ========== 提交处理 ==========
const handleSubmit = async () => {
  if (!profileFormRef.value) return

  // 验证表单
  const valid = await profileFormRef.value.validate().catch(() => false)
  if (!valid) return

  if (profileForm.interests.length === 0) {
    showInterestError.value = true
    ElMessage.warning('Please select at least one interest')
    return
  }

  loading.value = true

  try {
    await userStore.setupProfile(profileForm)
    
    ElMessage.success('Profile setup successful!')
    
    // 跳转到首页（Phase 2 完成后改为 '/plan/intent'）
    router.push('/')
  } catch (error) {
    console.error('Profile setup failed:', error)
    ElMessage.error(error.response?.data?.message || 'Failed to save profile')
  } finally {
    loading.value = false
  }
}

// ========== 地理位置检测 ==========
const detectLocation = async () => {
  // 检查浏览器是否支持 Geolocation
  if (!isGeolocationSupported()) {
    ElMessage.warning('Your browser does not support geolocation')
    return
  }

  detectingLocation.value = true
  locationHint.value = '📍 Detecting your location...'

  try {
    // 获取位置并转换为地址
    const address = await getLocationAddress()
    
    profileForm.location = address
    locationHint.value = `✅ Location detected: ${address}`
    ElMessage.success('Location detected successfully!')
    
    // 3秒后恢复默认提示
    setTimeout(() => {
      locationHint.value = '💡 Click "Detect" to auto-detect your location, or type manually'
    }, 3000)
  } catch (error) {
    console.error('Location detection failed:', error)
    
    let errorMsg = 'Failed to detect location'
    if (error.message.includes('denied')) {
      errorMsg = 'Location permission denied. Please enable location access in your browser.'
    } else if (error.message.includes('unavailable')) {
      errorMsg = 'Location information is unavailable. Please enter manually.'
    }
    
    locationHint.value = `❌ ${errorMsg}`
    ElMessage.error(errorMsg)
    
    // 使用默认值
    profileForm.location = 'Sydney'
  } finally {
    detectingLocation.value = false
  }
}

// ========== 组件挂载 ==========
onMounted(() => {
  // 页面加载时不自动检测位置，让用户手动触发
  // 这样更尊重用户隐私，且避免弹出位置权限请求吓到用户
})
</script>

<style scoped>
.profile-setup-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  padding: 40px 20px;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.05) 0%, rgba(118, 75, 162, 0.05) 100%);
}

.setup-card {
  width: 100%;
  max-width: 700px;
  background: white;
  border-radius: 16px;
  padding: 48px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

/* Progress Bar */
.progress-header {
  margin-bottom: 32px;
}

.progress-bar {
  height: 8px;
  background: #e4e7ed;
  border-radius: 4px;
  overflow: hidden;
  margin-bottom: 12px;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  transition: width 0.3s ease;
}

.progress-text {
  font-size: 14px;
  color: #909399;
  text-align: center;
}

/* Card Header */
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

/* Form Styles */
.profile-form {
  margin-top: 24px;
}

.hint-text {
  display: block;
  margin-top: 8px;
  font-size: 13px;
  color: #909399;
}

.error-text {
  display: block;
  margin-top: 8px;
  font-size: 13px;
  color: #f56c6c;
}

.full-width {
  width: 100%;
}

/* Button Group */
.button-group {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.button-group .el-button {
  height: 56px;
  font-size: 15px;
}

.button-icon {
  margin-right: 8px;
}

/* Checkbox Group */
.checkbox-group {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.checkbox-group .el-checkbox {
  margin: 0;
  padding: 12px 16px;
}

/* Submit Button */
.submit-button {
  width: 100%;
  margin-top: 24px;
  font-weight: 600;
  height: 48px;
}

/* 响应式 */
@media (max-width: 768px) {
  .setup-card {
    padding: 32px 24px;
  }

  .header-title {
    font-size: 24px;
  }

  .button-group {
    grid-template-columns: 1fr;
  }

  .checkbox-group {
    grid-template-columns: 1fr;
  }
}
</style>

