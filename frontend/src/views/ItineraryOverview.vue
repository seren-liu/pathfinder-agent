<template>
  <AppLayout>
    <div class="itinerary-overview">
      <!-- 加载状态 -->
      <div v-if="loading" class="loading-container">
        <el-skeleton :rows="10" animated />
      </div>

      <!-- 主内容 -->
      <div v-else-if="trip" class="overview-content">
        <!-- Header -->
        <div class="overview-header">
          <div class="header-left">
            <h1 class="trip-title">
              <el-icon><MapLocation /></el-icon>
              {{ trip.destinationName }}, {{ trip.destinationCountry }}
            </h1>
            <p class="trip-dates">
              {{ formatDate(trip.startDate) }} - {{ formatDate(trip.endDate) }}
              <el-tag type="info" size="small" style="margin-left: 10px">
                {{ trip.durationDays }} Days
              </el-tag>
            </p>
          </div>
          <div class="header-right">
            <el-button type="primary" @click.stop.prevent="handleStartEditing">
              <el-icon><Edit /></el-icon> Edit Itinerary
            </el-button>
          </div>
        </div>

        <!-- 预算统计 -->
        <el-row :gutter="20" class="stats-row">
          <el-col :span="8">
            <el-card shadow="hover" class="stat-card">
              <div class="stat-item">
                <el-icon class="stat-icon" :size="32" color="#67C23A"><Wallet /></el-icon>
                <div class="stat-content">
                  <p class="stat-label">Total Budget</p>
                  <p class="stat-value">${{ trip.totalBudget }}</p>
                </div>
              </div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="hover" class="stat-card">
              <div class="stat-item">
                <el-icon class="stat-icon" :size="32" color="#409EFF"><Money /></el-icon>
                <div class="stat-content">
                  <p class="stat-label">Actual Cost</p>
                  <p class="stat-value">${{ trip.actualTotalCost || 0 }}</p>
                </div>
              </div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="hover" class="stat-card">
              <div class="stat-item">
                <el-icon class="stat-icon" :size="32" :color="remainingBudgetColor">
                  <TrendCharts />
                </el-icon>
                <div class="stat-content">
                  <p class="stat-label">Remaining</p>
                  <p class="stat-value" :style="{ color: remainingBudgetColor }">
                    ${{ remainingBudget }}
                  </p>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>

        <!-- 每日行程列表 -->
        <div class="days-list">
          <el-card 
            v-for="day in trip.days" 
            :key="day.dayNumber" 
            class="day-card"
            shadow="hover"
          >
            <template #header>
              <div class="day-card-header">
                <div class="day-info">
                  <h3>Day {{ day.dayNumber }}: {{ day.theme || 'Exploring' }}</h3>
                  <p class="day-date">{{ formatDate(day.date) }}</p>
                </div>
                <div class="day-stats">
                  <el-tag type="info">{{ day.activityCount }} activities</el-tag>
                  <el-tag type="success">${{ day.totalCost || 0 }}</el-tag>
                  <el-button 
                    type="primary" 
                    size="small" 
                    @click="handleViewDay(day.dayNumber)"
                  >
                    View Details
                  </el-button>
                </div>
              </div>
            </template>

            <!-- 活动列表 -->
            <div class="activities-preview">
              <div 
                v-for="activity in (expandedDays.has(day.dayNumber) ? (day.activities || []) : (day.activities || []).slice(0, 3))" 
                :key="activity.activityId"
                class="activity-preview-item"
              >
                <el-icon :color="getActivityColor(activity.activityType)">
                  <component :is="getActivityIcon(activity.activityType)" />
                </el-icon>
                <span class="activity-time">{{ activity.startTime }}</span>
                <span class="activity-name">{{ activity.activityName }}</span>
                <span class="activity-cost">${{ activity.cost }}</span>
              </div>
              <div v-if="day.activities && day.activities.length > 3" class="expand-control">
                <el-button 
                  text 
                  size="small"
                  @click="toggleDay(day.dayNumber)"
                >
                  <el-icon>
                    <ArrowDown v-if="!expandedDays.has(day.dayNumber)" />
                    <ArrowUp v-else />
                  </el-icon>
                  {{ expandedDays.has(day.dayNumber) ? 'Show Less' : `Show ${(day.activities?.length || 0) - 3} More Activities` }}
                </el-button>
              </div>
            </div>
          </el-card>
        </div>
      </div>

      <!-- 错误状态 -->
      <el-empty v-else description="Trip not found" />
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getTripById } from '@/api/trips'
import AppLayout from '@/layouts/AppLayout.vue'
import { 
  MapLocation, Edit, Wallet, Money, TrendCharts,
  House, ForkSpoon, Compass, Van, Document, View, ArrowDown, ArrowUp
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const trip = ref(null)
const expandedDays = ref(new Set())

// 解析时间为分钟数（用于排序）
const parseTime = (time) => {
  if (!time) return 0
  let timeStr = time
  if (typeof time === 'string') {
    timeStr = time.length > 5 ? time.substring(0, 5) : time
  } else {
    timeStr = time.toString()
  }
  
  const parts = timeStr.split(':')
  if (parts.length >= 2) {
    const hours = parseInt(parts[0]) || 0
    const minutes = parseInt(parts[1]) || 0
    return hours * 60 + minutes
  }
  return 0
}

const toggleDay = (dayNumber) => {
  if (expandedDays.value.has(dayNumber)) {
    expandedDays.value.delete(dayNumber)
  } else {
    expandedDays.value.add(dayNumber)
  }
}

// 计算属性
const allActivities = computed(() => {
  if (!trip.value || !trip.value.days) return []
  return trip.value.days.flatMap(day => day.activities || [])
})

const mapCenter = computed(() => {
  if (!trip.value || !trip.value.destinationCenter) {
    return { lat: -28.6418, lng: 153.6123 }
  }
  return {
    lat: Number(trip.value.destinationCenter.latitude),
    lng: Number(trip.value.destinationCenter.longitude)
  }
})

const remainingBudget = computed(() => {
  if (!trip.value) return 0
  const remaining = Number(trip.value.totalBudget || 0) - Number(trip.value.actualTotalCost || 0)
  return remaining.toFixed(2)
})

const remainingBudgetColor = computed(() => {
  const remaining = Number(remainingBudget.value)
  if (remaining < 0) return '#F56C6C'
  if (remaining < Number(trip.value?.totalBudget || 0) * 0.2) return '#E6A23C'
  return '#67C23A'
})

// 方法
const fetchTrip = async () => {
  try {
    loading.value = true
    const tripId = route.query.tripId
    if (!tripId) {
      ElMessage.error('Trip ID is required')
      return
    }
    
    const tripData = await getTripById(tripId)
    console.log('API Response:', tripData)
    
    if (!tripData) {
      ElMessage.error('Failed to load trip data')
      return
    }
    
    trip.value = tripData
    
    if (!trip.value || !trip.value.days) {
      trip.value = trip.value || {}
      trip.value.days = []
    } else {
      // 对每个天的活动按时间排序
      trip.value.days.forEach(day => {
        if (day.activities && day.activities.length > 0) {
          day.activities.sort((a, b) => {
            const timeA = parseTime(a.startTime)
            const timeB = parseTime(b.startTime)
            return timeA - timeB
          })
        }
      })
    }
  } catch (error) {
    console.error('Failed to fetch trip:', error)
    ElMessage.error('Failed to load trip')
  } finally {
    loading.value = false
  }
}

const formatDate = (date) => {
  if (!date) return ''
  return new Date(date).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric'
  })
}

const handleViewDay = (dayNumber) => {
  router.push({
    name: 'ItineraryDayView',
    params: { 
      tripId: route.query.tripId,
      dayNumber: dayNumber
    }
  })
}

const handleStartEditing = () => {
  console.log('🔵 Edit按钮被点击了！')
  
  const tripId = route.query.tripId || trip.value?.tripId || route.params.tripId
  console.log('handleStartEditing - tripId:', tripId)
  console.log('route.query:', route.query)
  console.log('route.params:', route.params)
  console.log('trip.value:', trip.value)
  
  if (!tripId) {
    ElMessage.error('Trip ID is required. Check URL: ' + route.fullPath)
    console.error('❌ Trip ID缺失 - route:', route)
    return
  }
  
  const targetPath = `/itinerary/edit/${tripId}`
  console.log('✅ 准备导航到:', targetPath)
  
  router.push(targetPath).then(() => {
    console.log('✅ 导航到编辑页面成功')
  }).catch((error) => {
    console.error('❌ 导航到编辑页面失败:', error)
    ElMessage.error('Failed to navigate to editor: ' + (error.message || error))
    // 备用方案：直接使用window.location
    window.location.href = targetPath
  })
}

const getActivityColor = (type) => {
  const colors = {
    accommodation: '#E6A23C',
    dining: '#F56C6C',
    activity: '#409EFF',
    transportation: '#909399',
    other: '#67C23A'
  }
  return colors[type] || colors.other
}

const getActivityIcon = (type) => {
  const icons = {
    accommodation: House,
    dining: ForkSpoon,
    activity: Compass,
    transportation: Van,
    other: Compass
  }
  return icons[type] || Compass
}

onMounted(() => {
  fetchTrip()
})
</script>

<style scoped>
.itinerary-overview {
  max-width: 1400px;
  margin: 0 auto;
}

.loading-container {
  padding: 40px;
}

.overview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
  padding-bottom: 20px;
  border-bottom: 2px solid #f0f0f0;
}

.trip-title {
  font-size: 32px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 10px 0;
  display: flex;
  align-items: center;
  gap: 12px;
}

.trip-dates {
  font-size: 16px;
  color: #606266;
  margin: 0;
}

.stats-row {
  margin-bottom: 30px;
}

.stat-card {
  transition: transform 0.3s;
}

.stat-card:hover {
  transform: translateY(-5px);
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 20px;
}

.stat-icon {
  flex-shrink: 0;
}

.stat-content {
  flex: 1;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin: 0 0 8px 0;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}

.map-card {
  margin-bottom: 30px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
}

.days-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.day-card {
  transition: transform 0.3s, box-shadow 0.3s;
}

.day-card:hover {
  transform: translateX(5px);
  box-shadow: 0 4px 16px rgba(0,0,0,0.15);
}

.day-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.day-info h3 {
  margin: 0 0 5px 0;
  font-size: 20px;
  color: #303133;
}

.day-date {
  margin: 0;
  font-size: 14px;
  color: #909399;
}

.day-stats {
  display: flex;
  align-items: center;
  gap: 10px;
}

.activities-preview {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.activity-preview-item {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 10px;
  background: #f5f7fa;
  border-radius: 8px;
}

.activity-time {
  font-weight: 600;
  color: #409EFF;
  min-width: 60px;
}

.activity-name {
  flex: 1;
  color: #303133;
}

.activity-cost {
  font-weight: 600;
  color: #67C23A;
}

.more-activities {
  text-align: center;
  color: #909399;
  font-size: 14px;
  margin: 10px 0 0 0;
}

.expand-control {
  text-align: center;
  margin-top: 15px;
  padding-top: 15px;
  border-top: 1px solid #e4e7ed;
}
</style>
