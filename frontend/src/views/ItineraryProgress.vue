<template>
  <AppLayout>
    <div class="progress-container">
      <el-card class="progress-card">
        <template #header>
          <div class="card-header">
            <h2>🚀 Generating Your Itinerary</h2>
            <p class="subtitle">Please wait while AI creates your perfect travel plan...</p>
          </div>
        </template>

        <div class="progress-content">
          <!-- 进度条 -->
          <el-progress 
            :percentage="progress" 
            :status="progressStatus"
            :stroke-width="20"
          />

          <!-- 当前步骤 -->
          <div class="current-step">
            <el-icon class="step-icon" :size="24">
              <Loading v-if="progress < 100" />
              <Check v-else />
            </el-icon>
            <span class="step-text">{{ currentStep }}</span>
          </div>

          <!-- 步骤列表 -->
          <el-timeline class="steps-timeline">
            <el-timeline-item
              v-for="(step, index) in steps"
              :key="index"
              :icon="getStepIcon(index)"
              :type="getStepType(index)"
              :hollow="!isStepCompleted(index)"
            >
              {{ step }}
            </el-timeline-item>
          </el-timeline>

          <!-- 完成后的跳转按钮 -->
          <div v-if="progress >= 100 && progressStatus === 'success'" class="success-actions">
            <el-button type="primary" size="large" @click="handleViewItinerary">
              View Itinerary
            </el-button>
          </div>

          <!-- 提示信息 -->
          <el-alert
            v-if="progress < 100"
            title="This may take 15-20 seconds"
            type="info"
            :closable="false"
            show-icon
          >
            <template #default>
              <p>We're using AI to:</p>
              <ul>
                <li>Analyze your destination</li>
                <li>Find the best activities</li>
                <li>Optimize timing and routes</li>
                <li>Calculate costs</li>
              </ul>
            </template>
          </el-alert>

          <!-- 错误信息 -->
          <el-alert
            v-if="error"
            :title="error"
            type="error"
            show-icon
            @close="goBack"
          />
        </div>
      </el-card>
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading, Check, Clock, CircleCheck } from '@element-plus/icons-vue'
import { getTripStatus } from '@/api/trips'
import AppLayout from '@/layouts/AppLayout.vue'

const route = useRoute()
const router = useRouter()

// ========== 状态数据 ==========
const tripId = ref(route.params.tripId)
const progress = ref(0)
const currentStep = ref('Initializing...')
const error = ref(null)
const progressStatus = ref('')

const steps = [
  'Creating trip record',
  'Analyzing destination',
  'Generating daily plans',
  'Finding activities',
  'Optimizing schedule',
  'Saving itinerary'
]

let pollInterval = null

// ========== 获取进度 ==========
const fetchProgress = async () => {
  try {
    const data = await getTripStatus(tripId.value)
    
    // 确保 progress 在有效范围内 (0-100)
    const rawProgress = data.progress || 0
    progress.value = Math.max(0, Math.min(100, rawProgress))
    currentStep.value = data.currentStep || 'Processing...'
    
    // ✅ 修复：只要 status 是 completed 就跳转，不需要等 progress 100%
    // 因为后端现在是异步地理编码，创建完活动就设为 completed
    if (data.status === 'completed') {
      progressStatus.value = 'success'
      progress.value = 100  // 强制设为 100%
      ElMessage.success('Itinerary generated successfully!')
      
      // 停止轮询
      if (pollInterval) {
        clearInterval(pollInterval)
        pollInterval = null
      }
      
      // 延迟跳转，让用户看到完成状态
      setTimeout(() => {
        router.push({
          name: 'ItineraryOverview',
          query: { tripId: tripId.value }
        }).catch(err => {
          console.error('Navigation error:', err)
          ElMessage.error('Failed to navigate. Please refresh the page.')
        })
      }, 1000)
    } else if (data.status === 'failed') {
      progressStatus.value = 'exception'
      progress.value = 0
      error.value = data.errorMessage || 'Failed to generate itinerary. Please try again.'
      
      ElMessage.error({
        message: error.value,
        duration: 5000
      })
      
      // 停止轮询
      if (pollInterval) {
        clearInterval(pollInterval)
        pollInterval = null
      }
    }
  } catch (err) {
    console.error('Failed to fetch progress:', err)
    error.value = 'Failed to get generation status'
    progress.value = 0
    progressStatus.value = 'exception'
    
    // 停止轮询
    if (pollInterval) {
      clearInterval(pollInterval)
      pollInterval = null
    }
  }
}

// ========== 步骤状态 ==========
const isStepCompleted = (index) => {
  const stepProgress = ((index + 1) / steps.length) * 100
  return progress.value >= stepProgress
}

const getStepIcon = (index) => {
  if (isStepCompleted(index)) {
    return CircleCheck
  }
  return Clock
}

const getStepType = (index) => {
  if (isStepCompleted(index)) {
    return 'success'
  }
  return 'info'
}

// ========== 跳转到行程 ==========
const handleViewItinerary = () => {
  router.push({
    name: 'ItineraryOverview',
    query: { tripId: tripId.value }
  }).catch(err => {
    console.error('Manual navigation error:', err)
    ElMessage.error('Navigation failed: ' + err.message)
  })
}

// ========== 返回 ==========
const goBack = () => {
  router.push('/plan/destinations')
}

// ========== 生命周期 ==========
onMounted(() => {
  if (!tripId.value) {
    ElMessage.error('Invalid trip ID')
    goBack()
    return
  }
  
  // 立即获取一次进度
  fetchProgress()
  
  // 每 2 秒轮询一次
  pollInterval = setInterval(fetchProgress, 2000)
})

onUnmounted(() => {
  if (pollInterval) {
    clearInterval(pollInterval)
  }
})
</script>

<style scoped>
.progress-container {
  max-width: 800px;
  margin: 40px auto;
  padding: 20px;
}

.success-actions {
  margin: 30px 0;
  text-align: center;
}

.progress-card {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.card-header {
  text-align: center;
}

.card-header h2 {
  margin: 0 0 10px 0;
  color: #303133;
  font-size: 28px;
}

.subtitle {
  margin: 0;
  color: #909399;
  font-size: 14px;
}

.progress-content {
  padding: 20px 0;
}

.current-step {
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 30px 0;
  font-size: 18px;
  color: #409EFF;
}

.step-icon {
  margin-right: 10px;
}

.step-text {
  font-weight: 500;
}

.steps-timeline {
  margin: 40px 0;
  padding: 0 20px;
}

.el-alert {
  margin-top: 20px;
}

.el-alert ul {
  margin: 10px 0 0 20px;
  padding: 0;
}

.el-alert li {
  margin: 5px 0;
}

/* 动画效果 */
.step-icon {
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}
</style>
