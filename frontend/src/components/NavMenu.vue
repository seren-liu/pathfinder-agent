<!-- src/components/NavMenu.vue -->
<template>
  <el-menu mode="horizontal" class="nav-menu" :ellipsis="false" @select="handleMenuSelect">
    <el-menu-item index="logo" @click="goHome" class="logo-item">
      <el-icon class="logo-icon" :size="24"><Compass /></el-icon>
      <span class="logo-text">Pathfinder Agent</span>
    </el-menu-item>
    
    <!-- Trip Planning 子菜单 -->
    <el-sub-menu index="planning">
      <template #title>
        <span class="nav-title">Trip Planning</span>
      </template>
      <el-menu-item index="/plan/intent">
        <el-icon><Search /></el-icon>
        Start Planning
      </el-menu-item>
      <el-menu-item index="/plan/destinations">
        <el-icon><Location /></el-icon>
        Explore Destinations
      </el-menu-item>
    </el-sub-menu>

    <!-- My Trips 子菜单 -->
    <el-sub-menu index="trips">
      <template #title>
        <span class="nav-title">My Trips</span>
      </template>
      <el-menu-item @click="goToCurrentTrip">
        <el-icon><Suitcase /></el-icon>
        Current Trips
      </el-menu-item>
      <el-menu-item index="/trips/history">
        <el-icon><Clock /></el-icon>
        Past Trips
      </el-menu-item>
    </el-sub-menu>
    
    <div class="flex-grow" />
    
    <!-- 未登录：显示登录按钮 -->
    <el-menu-item v-if="!userStore.isLoggedIn" index="/login" class="login-button-item">
      <el-button type="primary" round>Login</el-button>
    </el-menu-item>

    <!-- 已登录：显示用户头像下拉菜单 -->
    <div v-else class="user-dropdown-container">
      <el-dropdown trigger="click" @command="handleCommand">
        <div class="user-avatar-wrapper">
          <el-avatar :size="40" :style="{ background: avatarColor }">
            <el-icon :size="20"><User /></el-icon>
          </el-avatar>
          <el-icon class="dropdown-icon"><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <!-- 用户信息头部 -->
            <div class="user-info-header">
              <el-avatar :size="50" :style="{ background: avatarColor }">
                <el-icon :size="24"><User /></el-icon>
              </el-avatar>
              <div class="user-info-text">
                <div class="user-email">{{ userStore.user?.email || 'User' }}</div>
                <el-tag size="small" type="success">{{ userStore.user?.status || 'Active' }}</el-tag>
              </div>
            </div>
            
            <el-dropdown-item divided command="profile">
              <el-icon><Setting /></el-icon>
              <span>My Profile</span>
            </el-dropdown-item>
            
            <!-- Display user preferences (if set) -->
            <template v-if="userStore.hasProfile">
              <el-dropdown-item disabled class="profile-info-item">
                <div class="profile-detail">
                  <div class="profile-row">
                    <el-icon><Location /></el-icon>
                    <span>{{ userStore.user?.location || 'N/A' }}</span>
                  </div>
                  <div class="profile-row">
                    <el-icon><User /></el-icon>
                    <span>{{ userStore.user?.ageRange || 'N/A' }}</span>
                  </div>
                  <div class="profile-row">
                    <el-icon><Suitcase /></el-icon>
                    <span>{{ formatTravelStyle(userStore.user?.travelStyle) }}</span>
                  </div>
                  <div class="profile-row" v-if="userStore.user?.interests?.length">
                    <el-icon><Star /></el-icon>
                    <span class="interests-tags">
                      <el-tag 
                        v-for="interest in userStore.user.interests.slice(0, 3)" 
                        :key="interest" 
                        size="small"
                        style="margin-right: 4px;"
                      >
                        {{ interest }}
                      </el-tag>
                    </span>
                  </div>
                  <div class="profile-row">
                    <el-icon><Money /></el-icon>
                    <span>{{ formatBudget(userStore.user?.budgetPreference) }}</span>
                  </div>
                </div>
              </el-dropdown-item>
            </template>
            
            <el-dropdown-item command="edit-profile" v-if="userStore.hasProfile">
              <el-icon><Edit /></el-icon>
              <span>Edit Preferences</span>
            </el-dropdown-item>
            
            <el-dropdown-item divided command="logout" class="logout-item">
              <el-icon><SwitchButton /></el-icon>
              <span>Logout</span>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </el-menu>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getLatestTrip } from '@/api/trips'
import { 
  Compass, 
  Search, 
  Location, 
  Suitcase, 
  Clock, 
  Document, 
  MagicStick, 
  Star,
  User,
  ArrowDown,
  Setting,
  Edit,
  SwitchButton,
  Money,
  Share
} from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()

// 根据邮箱生成头像颜色
const avatarColor = computed(() => {
  if (!userStore.user?.email) return '#667eea'
  
  const email = userStore.user.email
  const hash = email.split('').reduce((acc, char) => {
    return char.charCodeAt(0) + ((acc << 5) - acc)
  }, 0)
  
  const colors = [
    '#667eea', '#764ba2', '#f093fb', '#4facfe',
    '#43e97b', '#fa709a', '#fee140', '#30cfd0'
  ]
  
  return colors[Math.abs(hash) % colors.length]
})

const goHome = () => {
  router.push('/')
}

const handleMenuSelect = (index) => {
  // 如果是路由路径，进行跳转
  if (index.startsWith('/')) {
    router.push(index)
  }
}

const formatTravelStyle = (style) => {
  const styleMap = {
    'family': 'Family',
    'solo': 'Solo',
    'couple': 'Couple',
    'business': 'Business'
  }
  return styleMap[style] || style || 'N/A'
}

const formatBudget = (budget) => {
  const budgetMap = {
    1: '💰 Budget',
    2: '💰💰 Moderate',
    3: '💰💰💰 Luxury'
  }
  return budgetMap[budget] || 'N/A'
}

// 跳转到最新行程
const goToCurrentTrip = async () => {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('Please login first')
    router.push('/login')
    return
  }
  
  try {
    const res = await getLatestTrip(userStore.userId)
    if (res.data) {
      // 有最新行程，直接跳转到 Overview
      router.push({
        path: '/itinerary/overview',
        query: { tripId: res.data }
      })
    } else {
      // 没有行程，提示用户
      ElMessage.info('No trips found. Start planning your first trip!')
      router.push('/plan/intent')
    }
  } catch (error) {
    console.error('Failed to get latest trip:', error)
    ElMessage.error('Failed to load trips')
  }
}

const handleCommand = async (command) => {
  switch (command) {
    case 'profile':
      // Navigate to profile page (view mode)
      router.push(`/users/${userStore.userId}/profile`)
      break
      
    case 'edit-profile':
      // Navigate to profile page (will auto-enter edit mode via route query)
      router.push({
        path: `/users/${userStore.userId}/profile`,
        query: { edit: 'true' }
      })
      break
      
    case 'logout':
      // Logout confirmation
      try {
        await ElMessageBox.confirm(
          'Are you sure you want to logout?',
          'Confirm Logout',
          {
            confirmButtonText: 'Confirm',
            cancelButtonText: 'Cancel',
            type: 'warning'
          }
        )
        
        await userStore.logout()
        ElMessage.success('Logged out successfully')
        router.push('/')
      } catch (error) {
        // User cancelled
        if (error !== 'cancel') {
          console.error('Logout error:', error)
        }
      }
      break
  }
}
</script>

<style scoped>
.nav-menu {
  padding: 0 24px;
  height: 64px;
  line-height: 64px;
  background-color: white;
  border-radius: 12px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  border-bottom: none;
  width: 100%;
  display: flex;
  align-items: center;
}

.logo-item {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-right: 32px;
  cursor: pointer;
}

.logo-item:hover {
  background-color: transparent !important;
}

.logo-icon {
  color: #667eea;
}

.logo-text {
  font-size: 18px;
  font-weight: 700;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: 0.3px;
}

.nav-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.flex-grow {
  flex-grow: 1;
}

.login-button-item {
  border: none !important;
}

.el-button--primary {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  padding: 10px 24px;
  font-weight: 600;
}

.el-button--primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

/* 用户头像下拉菜单样式 */
.user-dropdown-container {
  display: flex;
  align-items: center;
  height: 64px;
  padding: 0 8px;
}

.user-avatar-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  transition: all 0.3s ease;
}

.user-avatar-wrapper:hover {
  background-color: #f5f7fa;
}

.dropdown-icon {
  color: #909399;
  font-size: 14px;
  transition: transform 0.3s ease;
}

.user-avatar-wrapper:hover .dropdown-icon {
  transform: translateY(2px);
}

/* 下拉菜单内容样式 */
.user-info-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: linear-gradient(135deg, #667eea15 0%, #764ba215 100%);
  border-radius: 8px 8px 0 0;
}

.user-info-text {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.user-email {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-info-item {
  cursor: default !important;
}

.profile-detail {
  padding: 8px 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.profile-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #606266;
}

.profile-row .el-icon {
  color: #909399;
  font-size: 14px;
}

.interests-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.logout-item {
  color: #f56c6c;
}

.logout-item:hover {
  background-color: #fef0f0;
}

:deep(.el-dropdown-menu) {
  min-width: 280px;
  padding: 0;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

:deep(.el-dropdown-menu__item) {
  padding: 12px 16px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

:deep(.el-dropdown-menu__item:hover) {
  background-color: #f5f7fa;
}

:deep(.el-dropdown-menu__item.is-disabled) {
  opacity: 1;
  cursor: default;
}

:deep(.el-dropdown-menu__item.is-disabled:hover) {
  background-color: white;
}

@media (max-width: 768px) {
  .nav-menu {
    padding: 0 12px;
    height: 56px;
    line-height: 56px;
  }
  
  .logo-text {
    font-size: 16px;
  }
  
  .nav-title {
    font-size: 14px;
  }

  .user-dropdown-container {
    height: 56px;
  }
  
  .user-avatar-wrapper {
    padding: 4px;
  }
  
  :deep(.el-dropdown-menu) {
    min-width: 240px;
  }
  
  .user-email {
    max-width: 140px;
  }
}
</style>