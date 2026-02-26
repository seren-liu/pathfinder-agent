<template>
  <AppLayout>
    <div class="generating-container">
      <el-card class="progress-card">
        <div class="progress-content">
          <el-icon class="loading-icon" :size="80" color="#409EFF">
            <Loading />
          </el-icon>
          
          <h2>Generating Your Personalized Itinerary...</h2>
          
          <el-progress 
            :percentage="progress" 
            :status="progressStatus"
            :stroke-width="20"
          />
          
          <p class="current-step">{{ currentStep }}</p>
          
          <el-button 
            v-if="status === 'failed'" 
            type="danger" 
            @click="handleRetry"
          >
            <el-icon><RefreshRight /></el-icon>
            Retry Generation
          </el-button>
        </div>
      </el-card>
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getTripStatus } from '@/api/trips'
import AppLayout from '@/layouts/AppLayout.vue'
import { Loading, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()

const progress = ref(0)
const currentStep = ref('Initializing...')
const status = ref('generating')
const progressStatus = ref('')

let pollingTimer = null

onMounted(() => {
  startPolling()
})

onUnmounted(() => {
  if (pollingTimer) {
    clearInterval(pollingTimer)
  }
})

const startPolling = () => {
  pollingTimer = setInterval(async () => {
    try {
      const tripId = route.query.tripId
      const data = await getTripStatus(tripId)
      
      progress.value = data.progress || 0
      currentStep.value = data.currentStep || 'Processing...'
      status.value = data.status
      
      // 生成完成
      if (status.value === 'completed') {
        progressStatus.value = 'success'
        clearInterval(pollingTimer)
        
        ElMessage.success('Itinerary generated successfully!')
        
        setTimeout(() => {
          router.push({
            name: 'ItineraryOverview',
            query: { tripId }
          })
        }, 1000)
      }
      
      // 生成失败
      if (status.value === 'failed') {
        progressStatus.value = 'exception'
        clearInterval(pollingTimer)
        ElMessage.error('Itinerary generation failed. Please try again.')
      }
      
    } catch (error) {
      console.error('Failed to get status:', error)
    }
  }, 2000) // 每2秒轮询一次
}

const handleRetry = () => {
  router.push({ name: 'Destinations' })
}
</script>

<style scoped>
.generating-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 60vh;
}

.progress-card {
  width: 600px;
  text-align: center;
}

.progress-content {
  padding: 40px;
}

.loading-icon {
  margin-bottom: 30px;
  animation: rotate 2s linear infinite;
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

h2 {
  margin: 20px 0;
  color: #303133;
  font-size: 24px;
  font-weight: 600;
}

.current-step {
  margin-top: 20px;
  color: #606266;
  font-size: 14px;
  min-height: 20px;
}
</style>
