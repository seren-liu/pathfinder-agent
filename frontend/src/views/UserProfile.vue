<!-- src/views/UserProfile.vue -->
<template>
  <AppLayout>
    <div class="user-profile-container">
      <el-card class="profile-card" v-loading="loading">
        <!-- 顶部用户信息 -->
        <div class="profile-header">
          <el-avatar :size="100" :style="{ background: avatarColor }">
            <el-icon :size="48"><User /></el-icon>
          </el-avatar>
          <div class="header-info">
            <h2 class="user-name">{{ profileData?.email || 'User' }}</h2>
            <el-tag :type="getStatusType(profileData?.status)">
              {{ profileData?.status || 'Active' }}
            </el-tag>
            <div class="user-meta">
              <el-icon><Calendar /></el-icon>
              <span>Joined: {{ formatDate(profileData?.createdAt) }}</span>
            </div>
          </div>
          <div class="header-actions">
            <el-button v-if="!isEditMode" type="primary" :icon="Edit" @click="toggleEditMode">
              Edit Profile
            </el-button>
            <template v-else>
              <el-button type="success" :icon="Check" @click="handleSave" :loading="saving">
                Save Changes
              </el-button>
              <el-button @click="cancelEdit">
                Cancel
              </el-button>
            </template>
          </div>
        </div>

        <el-divider />

        <!-- Personal Information -->
        <div class="profile-section">
          <div class="section-title">
            <el-icon><User /></el-icon>
            <h3>Personal Information</h3>
          </div>
          
          <!-- View Mode -->
          <el-row v-if="!isEditMode" :gutter="24">
            <el-col :span="12">
              <div class="info-item">
                <label>Current Location</label>
                <div class="info-value">
                  <el-icon><Location /></el-icon>
                  <span>{{ profileData?.location || 'Not set' }}</span>
                </div>
              </div>
            </el-col>
            <el-col :span="12">
              <div class="info-item">
                <label>Age Range</label>
                <div class="info-value">
                  <el-icon><User /></el-icon>
                  <span>{{ profileData?.ageRange || 'Not set' }}</span>
                </div>
              </div>
            </el-col>
            <el-col :span="12">
              <div class="info-item">
                <label>Language Preference</label>
                <div class="info-value">
                  <el-icon><ChatLineSquare /></el-icon>
                  <span>{{ formatLanguage(profileData?.language) }}</span>
                </div>
              </div>
            </el-col>
          </el-row>
          
          <!-- Edit Mode -->
          <el-form v-else ref="editFormRef" :model="editForm" label-position="top">
            <el-row :gutter="24">
              <el-col :span="12">
                <el-form-item label="Current Location">
                  <el-input v-model="editForm.location" placeholder="Sydney, NSW" size="large" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="Age Range">
                  <el-select v-model="editForm.ageRange" placeholder="Select age range" size="large" class="full-width">
                    <el-option label="18-30" value="18-30" />
                    <el-option label="31-50" value="31-50" />
                    <el-option label="51-65" value="51-65" />
                    <el-option label="65+" value="65+" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="Language Preference">
                  <el-select v-model="editForm.language" placeholder="Select language" size="large" class="full-width">
                    <el-option label="English" value="en" />
                    <el-option label="中文" value="zh" />
                    <el-option label="Español" value="es" />
                    <el-option label="Français" value="fr" />
                    <el-option label="Deutsch" value="de" />
                    <el-option label="日本語" value="ja" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </div>

        <el-divider />

        <!-- Travel Preferences -->
        <div class="profile-section">
          <div class="section-title">
            <el-icon><Suitcase /></el-icon>
            <h3>Travel Preferences</h3>
          </div>
          
          <!-- View Mode -->
          <el-row v-if="!isEditMode" :gutter="24">
            <el-col :span="12">
              <div class="info-item">
                <label>Travel Style</label>
                <div class="info-value">
                  <el-icon><Suitcase /></el-icon>
                  <span>{{ formatTravelStyle(profileData?.travelStyle) }}</span>
                </div>
              </div>
            </el-col>
            <el-col :span="12">
              <div class="info-item">
                <label>Budget Preference</label>
                <div class="info-value">
                  <el-icon><Money /></el-icon>
                  <span>{{ formatBudget(profileData?.budgetPreference) }}</span>
                </div>
              </div>
            </el-col>
            <el-col :span="24">
              <div class="info-item">
                <label>Interests</label>
                <div class="interests-tags">
                  <el-tag
                    v-for="interest in profileData?.interests || []"
                    :key="interest"
                    type="info"
                    effect="plain"
                    size="large"
                  >
                    {{ interest }}
                  </el-tag>
                  <el-tag v-if="!profileData?.interests?.length" type="info">
                    No interests set
                  </el-tag>
                </div>
              </div>
            </el-col>
          </el-row>
          
          <!-- Edit Mode -->
          <el-form v-else ref="editFormRef" :model="editForm" label-position="top">
            <el-row :gutter="24">
              <el-col :span="12">
                <el-form-item label="Travel Style">
                  <div class="button-group">
                    <el-button
                      v-for="style in travelStyles"
                      :key="style.value"
                      :type="editForm.travelStyle === style.value ? 'primary' : ''"
                      size="large"
                      @click="editForm.travelStyle = style.value"
                    >
                      {{ style.label }}
                    </el-button>
                  </div>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="Budget Preference">
                  <el-radio-group v-model="editForm.budgetPreference" size="large">
                    <el-radio :label="1">Budget ($)</el-radio>
                    <el-radio :label="2">Moderate ($$)</el-radio>
                    <el-radio :label="3">Luxury ($$$)</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item label="Interests">
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
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </div>

        <!-- Not set profile prompt -->
        <el-empty
          v-if="!hasProfile && !isEditMode"
          description="You haven't set up your profile yet"
          :image-size="200"
        >
          <el-button type="primary" @click="toggleEditMode">Set Up Now</el-button>
        </el-empty>
      </el-card>
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import {
  User,
  Edit,
  Check,
  Calendar,
  Location,
  Suitcase,
  Money,
  ChatLineSquare
} from '@element-plus/icons-vue'
import AppLayout from '@/layouts/AppLayout.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const saving = ref(false)
const profileData = ref(null)
const isEditMode = ref(false)
const editFormRef = ref(null)

// 编辑表单数据
const editForm = reactive({
  language: 'en',
  location: '',
  ageRange: '',
  travelStyle: '',
  interests: [],
  budgetPreference: 2
})

// 旅行风格选项
const travelStyles = [
  { label: 'Solo', value: 'solo' },
  { label: 'Family', value: 'family' },
  { label: 'Couple', value: 'couple' },
  { label: 'Business', value: 'business' }
]

// 兴趣选项
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

// 根据邮箱生成头像颜色
const avatarColor = computed(() => {
  if (!profileData.value?.email) return '#667eea'
  
  const email = profileData.value.email
  const hash = email.split('').reduce((acc, char) => {
    return char.charCodeAt(0) + ((acc << 5) - acc)
  }, 0)
  
  const colors = [
    '#667eea', '#764ba2', '#f093fb', '#4facfe',
    '#43e97b', '#fa709a', '#fee140', '#30cfd0'
  ]
  
  return colors[Math.abs(hash) % colors.length]
})

const hasProfile = computed(() => {
  return profileData.value?.location && profileData.value?.travelStyle
})

const getStatusType = (status) => {
  const statusMap = {
    'active': 'success',
    'inactive': 'info',
    'suspended': 'danger'
  }
  return statusMap[status] || 'info'
}

const formatDate = (dateString) => {
  if (!dateString) return 'Unknown'
  try {
    const date = new Date(dateString)
    return date.toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'long',
      day: 'numeric'
    })
  } catch {
    return dateString
  }
}

const formatLanguage = (lang) => {
  const langMap = {
    'en': 'English',
    'zh': '中文',
    'es': 'Español',
    'fr': 'Français',
    'de': 'Deutsch',
    'ja': '日本語'
  }
  return langMap[lang] || lang || 'English'
}

const formatTravelStyle = (style) => {
  const styleMap = {
    'family': '👨‍👩‍👧‍👦 Family',
    'solo': '🧳 Solo',
    'couple': '💑 Couple',
    'business': '💼 Business'
  }
  return styleMap[style] || style || 'Not set'
}

const formatBudget = (budget) => {
  const budgetMap = {
    1: '💰 Budget',
    2: '💰💰 Moderate',
    3: '💰💰💰 Luxury'
  }
  return budgetMap[budget] || 'Not set'
}

// 切换编辑模式
const toggleEditMode = () => {
  isEditMode.value = true
  // 复制当前数据到编辑表单
  editForm.language = profileData.value?.language || 'en'
  editForm.location = profileData.value?.location || ''
  editForm.ageRange = profileData.value?.ageRange || ''
  editForm.travelStyle = profileData.value?.travelStyle || ''
  editForm.budgetPreference = profileData.value?.budgetPreference || 2
  editForm.interests = [...(profileData.value?.interests || [])]
  
  // 设置兴趣复选框状态
  interestOptions.forEach(option => {
    selectedInterests[option.value] = editForm.interests.includes(option.value)
  })
}

// 取消编辑
const cancelEdit = () => {
  isEditMode.value = false
}

// 更新兴趣数组
const updateInterests = () => {
  editForm.interests = Object.keys(selectedInterests)
    .filter(key => selectedInterests[key])
}

// 保存修改
const handleSave = async () => {
  if (editForm.interests.length === 0) {
    ElMessage.warning('Please select at least one interest')
    return
  }
  
  saving.value = true
  try {
    await userStore.setupProfile(editForm)
    ElMessage.success('Profile updated successfully!')
    
    // 重新获取数据
    await fetchProfile()
    isEditMode.value = false
  } catch (error) {
    console.error('Failed to update profile:', error)
    ElMessage.error(error.response?.data?.message || 'Failed to update profile')
  } finally {
    saving.value = false
  }
}

const fetchProfile = async () => {
  loading.value = true
  try {
    const userId = route.params.userId || userStore.userId
    if (!userId) {
      ElMessage.error('User ID not found')
      router.push('/')
      return
    }

    await userStore.fetchUserDetail()
    profileData.value = userStore.user
  } catch (error) {
    console.error('Failed to fetch profile:', error)
    ElMessage.error('Failed to fetch user profile')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await fetchProfile()
  
  // 如果 URL 中有 edit=true 参数，自动进入编辑模式
  if (route.query.edit === 'true') {
    toggleEditMode()
  }
})
</script>

<style scoped>
.user-profile-container {
  max-width: 1000px;
  margin: 0 auto;
}

.profile-card {
  border-radius: 16px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

.profile-header {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 8px 0;
}

.header-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.user-name {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
  margin: 0;
}

.user-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #909399;
}

.header-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.profile-section {
  margin: 24px 0;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 20px;
}

.section-title h3 {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}

.section-title .el-icon {
  color: #667eea;
  font-size: 20px;
}

.info-item {
  margin-bottom: 20px;
}

.info-item label {
  display: block;
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
  font-weight: 500;
}

.info-value {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  color: #303133;
  padding: 12px;
  background-color: #f5f7fa;
  border-radius: 8px;
}

.info-value .el-icon {
  color: #667eea;
  font-size: 16px;
}

.interests-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 12px;
  background-color: #f5f7fa;
  border-radius: 8px;
}

/* Edit Mode Styles */
.full-width {
  width: 100%;
}

.button-group {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.button-group .el-button {
  height: 48px;
  font-size: 14px;
  border: 1px solid #dcdfe6;
  background-color: white;
  color: #606266;
}

.button-group .el-button:hover {
  color: #667eea;
  border-color: #667eea;
  background-color: #f5f7fa;
}

.button-group .el-button.el-button--primary {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-color: #667eea;
  color: white;
}

.button-group .el-button.el-button--primary:hover {
  opacity: 0.9;
}

.checkbox-group {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.checkbox-group .el-checkbox {
  margin: 0;
  padding: 12px 16px;
}

@media (max-width: 768px) {
  .profile-header {
    flex-direction: column;
    text-align: center;
  }

  .header-info {
    align-items: center;
  }

  .header-actions {
    width: 100%;
  }

  .header-actions .el-button {
    width: 100%;
  }
}
</style>

