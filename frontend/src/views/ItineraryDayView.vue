<template>
  <AppLayout>
    <div class="day-view">
      <!-- 加载状态 -->
      <div v-if="loading" class="loading-container">
        <el-skeleton :rows="8" animated />
      </div>

      <!-- 主内容 -->
      <div v-else-if="currentDay" class="day-content">
        <!-- Header -->
        <div class="day-header">
          <el-button @click="handleBack" :icon="ArrowLeft">
            Back to Overview
          </el-button>
          <div class="day-title">
            <h1>Day {{ currentDay.dayNumber }}: {{ currentDay.theme || 'Exploring' }}</h1>
            <p class="day-date">{{ formatDate(currentDay.date) }}</p>
          </div>
          <el-button type="primary" @click="handleEdit" :icon="Edit">
            Edit This Day
          </el-button>
        </div>

        <!-- 统计卡片 -->
        <el-row :gutter="20" class="day-stats">
          <el-col :span="8">
            <el-card shadow="hover">
              <div class="stat-item">
                <el-icon :size="24" color="#409EFF"><Calendar /></el-icon>
                <div>
                  <p class="stat-label">Activities</p>
                  <p class="stat-value">{{ currentDay.activityCount }}</p>
                </div>
              </div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="hover">
              <div class="stat-item">
                <el-icon :size="24" color="#67C23A"><Money /></el-icon>
                <div>
                  <p class="stat-label">Total Cost</p>
                  <p class="stat-value">${{ currentDay.totalCost || 0 }}</p>
                </div>
              </div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="hover">
              <div class="stat-item">
                <el-icon :size="24" color="#E6A23C"><Clock /></el-icon>
                <div>
                  <p class="stat-label">Duration</p>
                  <p class="stat-value">{{ formatDuration(currentDay.totalDuration) }}</p>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>

        <!-- 地图 -->
        <el-card class="map-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span><el-icon><Location /></el-icon> Day {{ currentDay.dayNumber }} Map & Route</span>
              <el-tag type="success" size="small">MapLibre GL</el-tag>
            </div>
          </template>
          <MapLibreView 
            :key="mapKey"
            :activities="currentDayActivities" 
            :center="mapCenter"
            :show-routes-default="true"
            map-height="500px"
          />
        </el-card>

        <!-- 时间线 -->
        <el-card class="timeline-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span><el-icon><Clock /></el-icon> Activity Timeline</span>
            </div>
          </template>
          
          <el-timeline>
            <el-timeline-item 
              v-for="activity in currentDay.activities" 
              :key="activity.activityId"
              :timestamp="activity.startTime"
              placement="top"
            >
              <el-card class="activity-card" shadow="hover">
                <div class="activity-header">
                  <div class="activity-title">
                    <el-icon 
                      :size="24" 
                      :color="getActivityColor(activity.activityType)"
                    >
                      <component :is="getActivityIcon(activity.activityType)" />
                    </el-icon>
                    <h3>{{ activity.activityName }}</h3>
                  </div>
                  <div class="activity-header-right">
                    <el-tag :type="getActivityTagType(activity.activityType)">
                      {{ activity.activityType }}
                    </el-tag>
                    <el-button 
                      text 
                      size="small"
                      @click="toggleActivity(activity.activityId)"
                    >
                      <el-icon>
                        <ArrowDown v-if="!expandedActivities.has(activity.activityId)" />
                        <ArrowUp v-else />
                      </el-icon>
                      {{ expandedActivities.has(activity.activityId) ? 'Show Less' : 'Show More' }}
                    </el-button>
                  </div>
                </div>

                <!-- 基本信息（始终显示） -->
                <div class="activity-basic-info">
                  <div class="detail-item">
                    <el-icon><Clock /></el-icon>
                    <span>{{ activity.durationMinutes }} minutes</span>
                  </div>
                  <div class="detail-item">
                    <el-icon><Money /></el-icon>
                    <span>${{ activity.cost || 0 }}</span>
                  </div>
                </div>

                <!-- 详细信息（可展开） -->
                <div v-if="expandedActivities.has(activity.activityId)" class="activity-expanded-details">
                  <div class="detail-item">
                    <el-icon><Location /></el-icon>
                    <span>{{ activity.location || 'Location TBD' }}</span>
                  </div>
                  
                  <p v-if="activity.notes" class="activity-notes">
                    <strong>Notes:</strong> {{ activity.notes }}
                  </p>

                  <div v-if="activity.bookingUrl" class="activity-actions">
                    <el-button 
                      type="primary" 
                      size="small" 
                      :icon="Link"
                      @click="openBookingUrl(activity.bookingUrl)"
                    >
                      Book Now
                    </el-button>
                  </div>
                </div>
              </el-card>
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </div>

      <!-- 错误状态 -->
      <el-empty v-else description="Day not found" />
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getTripById } from '@/api/trips'
import MapLibreView from '@/components/MapLibreView.vue'
import AppLayout from '@/layouts/AppLayout.vue'
import { useMapAutoRefresh } from '@/composables/useMapAutoRefresh'
import { 
  ArrowLeft, Edit, Calendar, Money, Clock, Location, Link,
  House, ForkSpoon, Compass, Van, ArrowDown, ArrowUp
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const trip = ref(null)
const mapKey = ref(0) // 用于强制刷新地图组件

// 计算属性
const currentDay = computed(() => {
  if (!trip.value || !trip.value.days) return null
  const dayNumber = parseInt(route.params.dayNumber)
  return trip.value.days.find(day => day.dayNumber === dayNumber)
})

const allActivities = computed(() => {
  if (!trip.value || !trip.value.days) return []
  return trip.value.days.flatMap(day => day.activities || [])
})

// 当前天的活动（用于地图显示）
const currentDayActivities = computed(() => {
  if (!currentDay.value || !currentDay.value.activities) return []
  return currentDay.value.activities
})

const mapCenter = computed(() => {
  if (!currentDay.value || !currentDay.value.activities || currentDay.value.activities.length === 0) {
    return { lat: -28.6418, lng: 153.6123 }
  }
  const firstActivity = currentDay.value.activities[0]
  if (firstActivity.latitude && firstActivity.longitude) {
    return {
      lat: Number(firstActivity.latitude),
      lng: Number(firstActivity.longitude)
    }
  }
  return { lat: -28.6418, lng: 153.6123 }
})

// 地图自动刷新
const { startPolling } = useMapAutoRefresh(
  computed(() => route.params.tripId),
  async () => {
      await fetchTrip()
      mapKey.value++ // 强制刷新地图组件
      ElMessage.success('Map updated with accurate locations!')
    }
)

// 方法
const fetchTrip = async () => {
  try {
    loading.value = true
    const tripId = route.params.tripId
    const dayNumber = Number(route.params.dayNumber)
    
    if (!tripId) {
      ElMessage.error('Trip ID is required')
      return
    }
    
    const data = await getTripById(tripId)
    trip.value = data
    
    if (!trip.value.days || trip.value.days.length === 0) {
      ElMessage.error('No itinerary data found')
      return
    }
    
    const day = trip.value.days.find(d => d.dayNumber === dayNumber)
    if (!day) {
      ElMessage.error(`Day ${dayNumber} not found`)
      return
    }
    
    // 对活动按时间排序
    if (day.activities && day.activities.length > 0) {
      day.activities.sort((a, b) => {
        const timeA = parseTime(a.startTime)
        const timeB = parseTime(b.startTime)
        return timeA - timeB
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
    weekday: 'long',
    month: 'long',
    day: 'numeric',
    year: 'numeric'
  })
}

const formatDuration = (minutes) => {
  if (!minutes) return '0h'
  const hours = Math.floor(minutes / 60)
  const mins = minutes % 60
  return mins > 0 ? `${hours}h ${mins}m` : `${hours}h`
}

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

// 展开/收起状态
const expandedActivities = ref(new Set())
const toggleActivity = (activityId) => {
  if (expandedActivities.value.has(activityId)) {
    expandedActivities.value.delete(activityId)
  } else {
    expandedActivities.value.add(activityId)
  }
}

const handleBack = () => {
  router.push({
    name: 'ItineraryOverview',
    query: { tripId: route.params.tripId }
  })
}

const handleEdit = () => {
  router.push({
    name: 'ItineraryEditor',
    params: { tripId: route.params.tripId }
  })
}

const openBookingUrl = (url) => {
  window.open(url, '_blank')
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

const getActivityTagType = (type) => {
  const types = {
    accommodation: 'warning',
    dining: 'danger',
    activity: 'primary',
    transportation: 'info',
    other: 'success'
  }
  return types[type] || 'info'
}

onMounted(async () => {
  await fetchTrip()
  
  // 检查是否需要启动轮询（如果活动没有坐标）
  const hasCoordinates = allActivities.value.some(activity => 
    activity.latitude && activity.longitude
  )
  
  if (!hasCoordinates || allActivities.value.length === 0) {
    startPolling()
  } else {
    // 检查坐标完整度
    const coordCount = allActivities.value.filter(a => a.latitude && a.longitude).length
    const totalCount = allActivities.value.length
    
    if (coordCount < totalCount * 0.5) {
      startPolling()
    } else {
      
    }
  }
})
</script>

<style scoped>
.day-view {
  max-width: 1200px;
  margin: 0 auto;
}

.loading-container {
  padding: 40px;
}

.day-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
  padding-bottom: 20px;
  border-bottom: 2px solid #f0f0f0;
}

.day-title h1 {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 8px 0;
}

.day-date {
  font-size: 16px;
  color: #606266;
  margin: 0;
}

.day-stats {
  margin-bottom: 30px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 15px;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin: 0 0 5px 0;
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}

.map-card,
.timeline-card {
  margin-bottom: 30px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
}

.activity-card {
  margin-top: 10px;
}

.activity-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.activity-header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.activity-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.activity-title h3 {
  margin: 0;
  font-size: 18px;
  color: #303133;
}

.activity-basic-info {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  margin-bottom: 15px;
}

.activity-expanded-details {
  margin-top: 15px;
  padding-top: 15px;
  border-top: 1px solid #e4e7ed;
}

.activity-details {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  margin-bottom: 15px;
}

.detail-item {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #606266;
  font-size: 14px;
}

.activity-notes {
  padding: 12px;
  background: #f5f7fa;
  border-radius: 8px;
  color: #606266;
  font-size: 14px;
  margin: 15px 0;
}

.activity-actions {
  margin-top: 15px;
}
</style>
