<template>
  <AppLayout>
    <div class="past-trips">
      <div class="header">
        <h1><el-icon><Clock /></el-icon> Past Trips</h1>
        <p class="subtitle">View and manage your travel history</p>
      </div>

      <el-loading v-loading="loading" />

      <!-- 行程列表 -->
      <div v-if="!loading && trips.length > 0" class="trips-grid">
        <el-card
          v-for="trip in trips"
          :key="trip.tripId"
          class="trip-card"
          shadow="hover"
          @click="handleViewTrip(trip.tripId)"
        >
          <template #header>
            <div class="trip-card-header">
              <div class="destination-info">
                <h3>{{ trip.destinationName }}</h3>
                <p class="country">{{ trip.destinationCountry }}</p>
              </div>
              <el-tag :type="getStatusType(trip.status)">
                {{ formatStatus(trip.status) }}
              </el-tag>
            </div>
          </template>

          <div class="trip-details">
            <div class="detail-item">
              <el-icon><Calendar /></el-icon>
              <span v-if="trip.startDate && trip.endDate">
                {{ formatDate(trip.startDate) }} - {{ formatDate(trip.endDate) }}
              </span>
              <span v-else-if="trip.startDate">
                {{ formatDate(trip.startDate) }}
              </span>
              <span v-else class="text-muted">Date TBD</span>
            </div>

            <div class="detail-item">
              <el-icon><Clock /></el-icon>
              <span>{{ trip.durationDays }} {{ trip.durationDays === 1 ? 'day' : 'days' }}</span>
            </div>

            <div class="detail-item">
              <el-icon><Money /></el-icon>
              <span>${{ trip.totalBudget ? trip.totalBudget.toLocaleString() : '0' }} AUD</span>
            </div>

            <div class="detail-item" v-if="trip.createdAt">
              <el-icon><Document /></el-icon>
              <span class="text-muted">Created: {{ formatDateTime(trip.createdAt) }}</span>
            </div>
          </div>

          <div class="trip-actions">
            <el-button type="primary" @click.stop="handleViewTrip(trip.tripId)">
              View Details
            </el-button>
          </div>
        </el-card>
      </div>

      <!-- 空状态 -->
      <el-empty
        v-if="!loading && trips.length === 0"
        description="No trips found"
      >
        <el-button type="primary" @click="goToPlanning">
          Start Planning
        </el-button>
      </el-empty>
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import {
  Clock,
  Calendar,
  Money,
  Document
} from '@element-plus/icons-vue'
import AppLayout from '@/layouts/AppLayout.vue'
import { getUserTrips } from '@/api/trips'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const trips = ref([])

const fetchTrips = async () => {
  try {
    loading.value = true
    if (!userStore.userId) {
      ElMessage.warning('Please login first')
      router.push('/login')
      return
    }

    const res = await getUserTrips(userStore.userId)
    if (res && res.code === 200) {
      trips.value = res.data || []
    } else {
      ElMessage.error(res?.message || 'Failed to load trips')
      trips.value = []
    }
  } catch (error) {
    console.error('Failed to fetch trips:', error)
    ElMessage.error('Failed to load trips')
    trips.value = []
  } finally {
    loading.value = false
  }
}

const handleViewTrip = (tripId) => {
  router.push({
    path: '/itinerary/overview',
    query: { tripId }
  })
}

const goToPlanning = () => {
  router.push('/plan/intent')
}

const formatDate = (date) => {
  if (!date) return ''
  const d = new Date(date)
  return d.toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  })
}

const formatDateTime = (datetime) => {
  if (!datetime) return ''
  const d = new Date(datetime)
  return d.toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  })
}

const formatStatus = (status) => {
  const statusMap = {
    'planning': 'Planning',
    'generating': 'Generating',
    'completed': 'Completed',
    'ongoing': 'Ongoing',
    'cancelled': 'Cancelled'
  }
  return statusMap[status] || status || 'Unknown'
}

const getStatusType = (status) => {
  const typeMap = {
    'planning': 'info',
    'generating': 'warning',
    'completed': 'success',
    'ongoing': 'primary',
    'cancelled': 'danger'
  }
  return typeMap[status] || 'info'
}

onMounted(() => {
  fetchTrips()
})
</script>

<style scoped>
.past-trips {
  min-height: 600px;
}

.header {
  margin-bottom: 30px;
  text-align: center;
}

.header h1 {
  font-size: 32px;
  margin-bottom: 10px;
  color: #303133;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.subtitle {
  color: #909399;
  font-size: 16px;
}

.trips-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
  gap: 20px;
  margin-top: 30px;
}

.trip-card {
  cursor: pointer;
  transition: all 0.3s ease;
  border-radius: 12px;
}

.trip-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.15);
}

.trip-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.destination-info h3 {
  margin: 0 0 5px 0;
  font-size: 20px;
  color: #303133;
}

.country {
  margin: 0;
  color: #909399;
  font-size: 14px;
}

.trip-details {
  margin: 20px 0;
}

.detail-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  color: #606266;
  font-size: 14px;
}

.detail-item .el-icon {
  color: #909399;
}

.text-muted {
  color: #909399;
  font-size: 13px;
}

.trip-actions {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid #e4e7ed;
}

.trip-actions .el-button {
  width: 100%;
}

@media (max-width: 768px) {
  .trips-grid {
    grid-template-columns: 1fr;
  }

  .header h1 {
    font-size: 24px;
  }
}
</style>

