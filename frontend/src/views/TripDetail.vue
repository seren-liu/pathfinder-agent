<template>
  <AppLayout>
    <div class="trip-detail-container">
      <el-skeleton v-if="loading" :rows="10" animated />
      
      <div v-else-if="trip" class="trip-content">
        <!-- 头部信息 -->
        <el-card class="header-card">
          <div class="trip-header">
            <div class="destination-info">
              <h1>{{ trip.destinationName }}</h1>
              <p class="country">{{ trip.destinationCountry }}</p>
              <el-tag :type="getStatusType(trip.status)">{{ trip.status }}</el-tag>
            </div>
            <div class="trip-meta">
              <div class="meta-item">
                <el-icon><Calendar /></el-icon>
                <span>{{ trip.durationDays }} days</span>
              </div>
              <div class="meta-item">
                <el-icon><User /></el-icon>
                <span>{{ trip.partySize }} {{ trip.partySize > 1 ? 'people' : 'person' }}</span>
              </div>
              <div class="meta-item">
                <el-icon><Money /></el-icon>
                <span>{{ trip.currency }} ${{ trip.totalBudget }}</span>
              </div>
            </div>
          </div>
        </el-card>

        <!-- 行程详情 -->
        <div class="itinerary-section">
          <h2>📅 Itinerary</h2>
          
          <el-empty v-if="!days || days.length === 0" description="No itinerary data" />
          
          <el-collapse v-else v-model="activeDays" accordion>
            <el-collapse-item
              v-for="day in days"
              :key="day.dayNumber"
              :name="day.dayNumber"
            >
              <template #title>
                <div class="day-header">
                  <span class="day-number">Day {{ day.dayNumber }}</span>
                  <span class="day-theme">{{ day.theme }}</span>
                  <span class="day-date" v-if="day.date">{{ formatDate(day.date) }}</span>
                </div>
              </template>
              
              <div class="day-content">
                <el-timeline>
                  <el-timeline-item
                    v-for="item in day.activities"
                    :key="item.activityId"
                    :timestamp="item.startTime"
                    placement="top"
                  >
                    <el-card class="activity-card">
                      <div class="activity-header">
                        <h4>{{ item.activityName }}</h4>
                        <el-tag size="small">{{ item.activityType }}</el-tag>
                      </div>
                      <div class="activity-details">
                        <p v-if="item.location">
                          <el-icon><Location /></el-icon>
                          {{ item.location }}
                        </p>
                        <p v-if="item.durationMinutes">
                          <el-icon><Clock /></el-icon>
                          {{ item.durationMinutes }} minutes
                        </p>
                        <p v-if="item.cost > 0">
                          <el-icon><Money /></el-icon>
                          ${{ item.cost }}
                        </p>
                      </div>
                      <p v-if="item.notes" class="activity-notes">{{ item.notes }}</p>
                    </el-card>
                  </el-timeline-item>
                </el-timeline>
              </div>
            </el-collapse-item>
          </el-collapse>
        </div>

        <!-- 操作按钮 -->
        <div class="actions">
          <el-button @click="goBack">Back to Planning</el-button>
          <el-button type="primary" @click="editItinerary">Edit Itinerary</el-button>
        </div>
      </div>

      <el-empty v-else description="Trip not found" />
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Calendar, User, Money, Location, Clock } from '@element-plus/icons-vue'
import { getTripById } from '@/api/trips'
import AppLayout from '@/layouts/AppLayout.vue'

const route = useRoute()
const router = useRouter()

// ========== 状态数据 ==========
const tripId = ref(route.params.tripId)
const loading = ref(true)
const trip = ref(null)
const days = ref([])
const activeDays = ref([])

// ========== 获取行程详情 ==========
const fetchTripDetail = async () => {
  try {
    loading.value = true
    const data = await getTripById(tripId.value)
    
    // ✅ 后端返回的是 TripResponse 对象，直接使用
    
    // 组装 trip 对象
    trip.value = {
      id: data.tripId,
      destinationName: data.destinationName,
      destinationCountry: data.destinationCountry,
      durationDays: data.durationDays,
      partySize: data.partySize,
      totalBudget: data.totalBudget,
      currency: 'AUD',
      status: data.status
    }
    
    // 获取 days 数据
    days.value = data.days || []
    
    // 默认展开第一天
    if (days.value.length > 0) {
      activeDays.value = [days.value[0].dayNumber]
    }
  } catch (error) {
    console.error('Failed to fetch trip detail:', error)
    ElMessage.error('Failed to load trip details')
  } finally {
    loading.value = false
  }
}

// ========== 辅助函数 ==========
const getStatusType = (status) => {
  const typeMap = {
    'planning': 'info',
    'generating': 'warning',
    'confirmed': 'success',
    'ongoing': 'primary',
    'completed': 'success'
  }
  return typeMap[status] || 'info'
}

const formatDate = (dateStr) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('en-US', { 
    weekday: 'short', 
    month: 'short', 
    day: 'numeric' 
  })
}

// ========== 操作 ==========
const goBack = () => {
  router.push('/plan/intent')
}

const editItinerary = () => {
  ElMessage.info('Edit feature coming soon!')
}

// ========== 生命周期 ==========
onMounted(() => {
  if (!tripId.value) {
    ElMessage.error('Invalid trip ID')
    goBack()
    return
  }
  
  fetchTripDetail()
})
</script>

<style scoped>
.trip-detail-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

.header-card {
  margin-bottom: 30px;
}

.trip-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.destination-info h1 {
  margin: 0 0 5px 0;
  font-size: 32px;
  color: #303133;
}

.country {
  margin: 0 0 10px 0;
  color: #909399;
  font-size: 16px;
}

.trip-meta {
  display: flex;
  gap: 20px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 5px;
  color: #606266;
}

.itinerary-section {
  margin-bottom: 30px;
}

.itinerary-section h2 {
  margin-bottom: 20px;
  color: #303133;
}

.day-header {
  display: flex;
  align-items: center;
  gap: 15px;
  width: 100%;
}

.day-number {
  font-weight: bold;
  color: #409EFF;
}

.day-theme {
  flex: 1;
  color: #303133;
}

.day-date {
  color: #909399;
  font-size: 14px;
}

.day-content {
  padding: 20px 0;
}

.activity-card {
  margin-bottom: 10px;
}

.activity-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.activity-header h4 {
  margin: 0;
  color: #303133;
}

.activity-details {
  display: flex;
  flex-wrap: wrap;
  gap: 15px;
  margin-bottom: 10px;
}

.activity-details p {
  display: flex;
  align-items: center;
  gap: 5px;
  margin: 0;
  color: #606266;
  font-size: 14px;
}

.activity-notes {
  margin: 10px 0 0 0;
  padding: 10px;
  background-color: #f5f7fa;
  border-radius: 4px;
  color: #606266;
  font-size: 14px;
}

.actions {
  display: flex;
  justify-content: center;
  gap: 20px;
  margin-top: 30px;
}
</style>
