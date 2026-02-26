<template>
  <AppLayout>
    <div class="destinations-container">
    <!-- Header -->
    <div class="page-header">
      <el-button :icon="ArrowLeft" @click="goBack">Back to Intent</el-button>
      <h2 class="page-title">🌏 We found these perfect destinations for you</h2>
      <el-button type="primary" @click="handleNextBatch" :loading="loading">
        <el-icon><Refresh /></el-icon>
        Change Batch
      </el-button>
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="loading-section">
      <el-skeleton :rows="5" animated />
    </div>

    <!-- Destinations Grid -->
    <div v-else-if="destinations.length > 0" class="destinations-wrapper">
      <el-row :gutter="32" class="destinations-grid">
      <el-col
        v-for="(dest, index) in destinations"
        :key="index"
        :xs="24"
        :sm="24"
        :md="8"
        :lg="8"
        :xl="8"
      >
        <el-card
          class="destination-card"
          :class="{ selected: selectedDestination?.name === dest.name && selectedDestination?.country === dest.country }"
          shadow="hover"
          @click="selectDestination(dest)"
        >
          <!-- Image -->
          <div class="card-image">
            <img
              :src="dest.imageUrl"
              :alt="dest.name"
              loading="lazy"
              decoding="async"
              width="480"
              height="320"
            />
            <div class="match-badge">
              <el-icon><StarFilled /></el-icon>
              {{ dest.matchScore }}%
            </div>
          </div>

          <!-- Content -->
          <div class="card-content">
            <h3 class="destination-name">{{ dest.name }}, {{ dest.state }}</h3>
            <p class="destination-country">📍 {{ dest.country }}</p>

            <!-- Tags -->
            <div class="destination-tags">
              <el-tag
                v-for="feature in dest.features.slice(0, 3)"
                :key="feature"
                size="small"
                type="info"
              >
                {{ feature }}
              </el-tag>
            </div>

            <!-- Budget & Season -->
            <div class="destination-info">
              <div class="info-item">
                <el-icon><Money /></el-icon>
                <span>{{ getBudgetLabel(dest.budgetLevel) }}: ${{ dest.estimatedCost }} ({{ dest.recommendedDays || 3 }} days)</span>
              </div>
              <div class="info-item">
                <el-icon><Calendar /></el-icon>
                <span>Best: {{ dest.bestSeason }}</span>
              </div>
            </div>

            <!-- AI Recommendation -->
            <div class="recommendation-reason">
              <el-icon><ChatDotRound /></el-icon>
              <p>{{ dest.recommendReason }}</p>
            </div>

            <!-- Actions -->
            <div class="card-actions">
              <el-button size="small" @click.stop="viewDetails(dest)">
                View Details
              </el-button>
              <el-button
                type="primary"
                size="small"
                :disabled="selectedDestination?.name === dest.name && selectedDestination?.country === dest.country"
                @click.stop="selectDestination(dest)"
              >
                {{ (selectedDestination?.name === dest.name && selectedDestination?.country === dest.country) ? 'Selected' : 'Select' }}
              </el-button>
            </div>
          </div>
        </el-card>
      </el-col>
      </el-row>
    </div>

    <!-- Empty State -->
    <el-empty
      v-else
      description="No destinations found matching your preferences"
    >
      <el-button type="primary" @click="goBack">Adjust Preferences</el-button>
    </el-empty>

    <!-- Confirm Button -->
    <div v-if="selectedDestination" class="confirm-section">
      <el-button
        type="success"
        size="large"
        class="confirm-button"
        @click="confirmSelection"
      >
        <el-icon><Check /></el-icon>
        Confirm Selection & Generate Itinerary
      </el-button>
    </div>

    <!-- Detail Modal -->
    <el-dialog
      v-model="detailDialogVisible"
      :title="`${currentDetail?.name}, ${currentDetail?.state} - Details`"
      width="600px"
    >
      <div v-if="currentDetail" class="detail-content">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="Location">
            {{ currentDetail.name }}, {{ currentDetail.state }}, {{ currentDetail.country }}
          </el-descriptions-item>
          <el-descriptions-item label="Estimated Cost">
            ${{ currentDetail.estimatedCost }} for {{ currentDetail.recommendedDays || 3 }} days
          </el-descriptions-item>
          <el-descriptions-item label="Best Time to Visit">
            {{ currentDetail.bestSeason }}
          </el-descriptions-item>
          <el-descriptions-item label="Budget Level">
            {{ getBudgetLabel(currentDetail.budgetLevel) }}
          </el-descriptions-item>
          <el-descriptions-item label="Key Features">
            <el-tag
              v-for="feature in currentDetail.features"
              :key="feature"
              class="feature-tag"
            >
              {{ feature }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="Match Score">
            <el-progress
              :percentage="currentDetail.matchScore"
              :color="getProgressColor(currentDetail.matchScore)"
            />
          </el-descriptions-item>
        </el-descriptions>

        <div class="detail-description">
          <h4>Description:</h4>
          <p>{{ currentDetail.description }}</p>
        </div>

        <div class="detail-recommendation">
          <h4>Why it matches:</h4>
          <p>{{ currentDetail.recommendReason }}</p>
        </div>
      </div>

      <template #footer>
        <el-button @click="detailDialogVisible = false">Close</el-button>
        <el-button type="primary" @click="selectFromDetail">
          Select This Destination
        </el-button>
      </template>
    </el-dialog>
  </div>
  </AppLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  Refresh,
  StarFilled,
  Money,
  Calendar,
  ChatDotRound,
  Check
} from '@element-plus/icons-vue'
import { recommendDestinationsApi } from '@/api/ai'
import { generateItinerary } from '@/api/trips'
import { useUserStore } from '@/stores/user'
import AppLayout from '@/layouts/AppLayout.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

// ========== 状态数据 ==========
const destinations = ref([])
const parsedIntent = ref(null)
const selectedDestination = ref(null)
const loading = ref(false)
const excludeIds = ref([])

// Detail Modal
const detailDialogVisible = ref(false)
const currentDetail = ref(null)

// ========== 生命周期 ==========
onMounted(async () => {
  // 从路由 query 参数获取解析的意图
  const intentQuery = route.query.intent
  
  if (intentQuery) {
    try {
      // 解码并解析 JSON（旧格式）
      const decodedIntent = decodeURIComponent(intentQuery)
      parsedIntent.value = JSON.parse(decodedIntent)
      
      await fetchDestinations()
    } catch (error) {
      console.error('Failed to parse intent from query:', error)
      ElMessage.error('Invalid intent data')
      router.push('/plan/intent')
    }
  } else if (route.query.sessionId) {
    // 新格式：从 Agent 传递的分散参数构建 intent
    parsedIntent.value = {
      destination: route.query.destination || '',  // 添加目的地
      interests: route.query.interests ? route.query.interests.split(',') : [],
      mood: route.query.mood || 'relaxing',
      budget: route.query.budget || '10000',
      days: parseInt(route.query.days) || 5,
      sessionId: route.query.sessionId
    }
    
    await fetchDestinations()
  } else {
    ElMessage.warning('Please start from intent input')
    router.push('/plan/intent')
  }
})

// ========== 获取目的地 ==========
const fetchDestinations = async (forceRefresh = false) => {
  loading.value = true

  try {
    // 构建请求参数
    const requestData = {
      userId: userStore.userId,
      sessionId: parsedIntent.value.sessionId || route.query.sessionId,
      destination: parsedIntent.value.destination || '',  // 添加目的地
      interests: parsedIntent.value.interests || [],
      mood: parsedIntent.value.mood || 'relaxing',
      budget: parsedIntent.value.budget || '10000',
      days: parsedIntent.value.days || 5
    }
    
    // 构建 excludeNames（目的地名称列表）
    const excludeNames = destinations.value.map(d => d.name)
    
    const response = await recommendDestinationsApi({
      ...requestData,
      excludeIds: excludeIds.value,
      excludeNames: excludeNames,
      forceRefresh: forceRefresh
    })

    destinations.value = response
    
    // 记录已显示的 ID（使用 destinationId，如果为 null 则跳过）
    response.forEach(dest => {
      if (dest.destinationId && !excludeIds.value.includes(dest.destinationId)) {
        excludeIds.value.push(dest.destinationId)
      }
    })

    if (destinations.value.length === 0) {
      ElMessage.warning('No more destinations found. Try adjusting your preferences.')
    }

  } catch (error) {
    console.error('Failed to fetch destinations:', error)
    ElMessage.error(error.response?.data?.message || 'Failed to load destinations')
  } finally {
    loading.value = false
  }
}

// ========== 换一批 ==========
const handleNextBatch = async () => {
  // 清空当前选中的目的地
  selectedDestination.value = null
  
  // 强制刷新，跳过缓存，重新调用 AI
  // excludeNames 会在 fetchDestinations 中自动构建
  await fetchDestinations(true)
}

// ========== 选择目的地 ==========
const selectDestination = (destination) => {
  selectedDestination.value = destination
  ElMessage.success(`Selected: ${destination.name}`)
}

// ========== 查看详情 ==========
const viewDetails = (destination) => {
  currentDetail.value = destination
  detailDialogVisible.value = true
}

const selectFromDetail = () => {
  selectDestination(currentDetail.value)
  detailDialogVisible.value = false
}

// ========== 确认选择 ==========
const confirmSelection = async () => {
  if (!selectedDestination.value) {
    ElMessage.warning('Please select a destination first')
    return
  }

  try {
    await ElMessageBox.confirm(
      `Generate itinerary for ${selectedDestination.value.name}?`,
      'Confirm Selection',
      {
        confirmButtonText: 'Yes, Generate Itinerary',
        cancelButtonText: 'Cancel',
        type: 'info'
      }
    )

    loading.value = true
    
    // ✅ 优先使用用户输入的原始预算与天数，避免默认值覆盖
    const durationDays = resolveDurationDays()
    const totalBudget = resolveTotalBudget(durationDays)
    
    const tripId = await generateItinerary({
      userId: userStore.userId,
      
      // ✅ 方案 B: 传递目的地信息（必填）
      destinationName: selectedDestination.value.name,
      destinationCountry: selectedDestination.value.country,
      destinationLatitude: selectedDestination.value.latitude,
      destinationLongitude: selectedDestination.value.longitude,
      
      // destinationId 可选（如果有就传，没有就不传）
      destinationId: selectedDestination.value.destinationId || selectedDestination.value.id || null,
      
      // 其他参数
      durationDays: durationDays,
      totalBudget: totalBudget,
      partySize: 1,
      preferences: resolvePreferences()
    })
    
    // 跳转到生成进度页面
    router.push({
      name: 'ItineraryProgress',
      params: { tripId }
    })

  } catch (error) {
    if (error !== 'cancel') {
      console.error('Failed to generate itinerary:', error)
      ElMessage.error('Failed to start itinerary generation')
    }
  } finally {
    loading.value = false
  }
}

// ========== 返回 ==========
const goBack = () => {
  router.push('/plan/intent')
}

// ========== 辅助函数 ==========
const getBudgetLabel = (level) => {
  return { 1: 'Budget', 2: 'Moderate', 3: 'Luxury' }[level] || 'Moderate'
}

const getProgressColor = (score) => {
  if (score >= 80) return '#67c23a'
  if (score >= 60) return '#e6a23c'
  return '#f56c6c'
}

const resolveDurationDays = () => {
  const candidates = [
    parsedIntent.value?.days,
    parsedIntent.value?.estimatedDuration,
    route.query.days,
    selectedDestination.value?.recommendedDays
  ]

  for (const candidate of candidates) {
    const normalized = parseInt(candidate, 10)
    if (!Number.isNaN(normalized) && normalized > 0) {
      return normalized
    }
  }
  return 5
}

const resolveTotalBudget = (durationDays) => {
  const explicitBudget = parseBudgetValue(
    parsedIntent.value?.budget ?? parsedIntent.value?.totalBudget ?? route.query.budget
  )
  if (explicitBudget !== null) {
    return explicitBudget
  }

  const budgetLevel = Number(parsedIntent.value?.budgetLevel || selectedDestination.value?.budgetLevel || 2)
  const dailyCost = { 1: 150, 2: 300, 3: 500 }[budgetLevel] || 300
  return dailyCost * durationDays
}

const parseBudgetValue = (rawBudget) => {
  if (rawBudget === null || rawBudget === undefined) return null
  if (typeof rawBudget === 'number' && Number.isFinite(rawBudget) && rawBudget > 0) {
    return Math.round(rawBudget)
  }

  const text = String(rawBudget).trim().toLowerCase()
  if (!text) return null

  const numberMatches = Array.from(text.matchAll(/(\d+(?:\.\d+)?)/g))
  if (!numberMatches.length) return null

  let value = Math.max(...numberMatches.map(match => Number.parseFloat(match[1])))
  if (!Number.isFinite(value) || value <= 0) return null

  if (text.includes('万')) {
    value *= 10000
  } else if (text.includes('k')) {
    value *= 1000
  }

  return Math.round(value)
}

const resolvePreferences = () => {
  const interests = parsedIntent.value?.interests || parsedIntent.value?.keywords || []
  return Array.isArray(interests) ? interests.join(', ') : ''
}
</script>

<style scoped>
.destinations-container {
  padding: 20px;
}

.page-header {
  max-width: 1400px;
  margin: 0 auto 40px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
  flex: 1;
  text-align: center;
}

.destinations-wrapper {
  width: 100%;
  display: flex;
  justify-content: center;
  padding: 0 20px;
}

.destinations-grid {
  max-width: 1400px;
  width: 100%;
  justify-content: center !important;
}

.destination-card {
  margin-bottom: 24px;
  cursor: pointer;
  transition: all 0.3s ease;
  border: 2px solid #e4e7ed;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  height: 680px;
  display: flex;
  flex-direction: column;
  position: relative;
}

.destination-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  border-radius: 8px;
  padding: 3px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
  -webkit-mask-composite: xor;
  mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
  mask-composite: exclude;
  opacity: 0;
  transition: opacity 0.3s ease;
  pointer-events: none;
}

.destination-card.selected {
  border-color: #667eea;
  border-width: 3px;
  box-shadow: 0 8px 32px rgba(102, 126, 234, 0.3);
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.05) 0%, rgba(118, 75, 162, 0.05) 100%);
  transform: scale(1.02);
}

.destination-card.selected::before {
  opacity: 1;
}

.destination-card.selected::after {
  content: '✓ Selected';
  position: absolute;
  top: 12px;
  left: 12px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 6px 16px;
  border-radius: 20px;
  font-weight: 600;
  font-size: 13px;
  z-index: 10;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
  animation: slideInLeft 0.3s ease;
}

@keyframes slideInLeft {
  from {
    opacity: 0;
    transform: translateX(-20px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.destination-card:hover {
  transform: translateY(-6px);
  border-color: #667eea;
  box-shadow: 0 8px 24px rgba(102, 126, 234, 0.2);
}

.destination-card.selected:hover {
  transform: scale(1.02) translateY(-4px);
}

.card-image {
  position: relative;
  width: 100%;
  height: 200px;
  overflow: hidden;
  border-radius: 8px 8px 0 0;
}

.card-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.match-badge {
  position: absolute;
  top: 12px;
  right: 12px;
  background: rgba(255, 255, 255, 0.95);
  padding: 6px 12px;
  border-radius: 20px;
  font-weight: 600;
  color: #f56c6c;
  display: flex;
  align-items: center;
  gap: 4px;
}

.card-content {
  padding: 20px;
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.destination-name {
  font-size: 20px;
  font-weight: 700;
  color: #303133;
  margin-bottom: 4px;
}

.destination-country {
  font-size: 14px;
  color: #909399;
  margin-bottom: 12px;
}

.destination-tags {
  margin-bottom: 16px;
}

.destination-tags .el-tag {
  margin-right: 8px;
  margin-bottom: 8px;
}

.destination-info {
  margin-bottom: 16px;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #606266;
  margin-bottom: 8px;
}

.recommendation-reason {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 8px;
  margin-bottom: 16px;
  display: flex;
  gap: 8px;
  flex: 1;
  overflow: hidden;
  min-height: 120px;
  max-height: 180px;
}

.recommendation-reason p {
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
  flex: 1;
  overflow-y: auto;
  margin: 0;
  padding-right: 8px;
}

.recommendation-reason p::-webkit-scrollbar {
  width: 6px;
}

.recommendation-reason p::-webkit-scrollbar-track {
  background: #e4e7ed;
  border-radius: 3px;
}

.recommendation-reason p::-webkit-scrollbar-thumb {
  background: #909399;
  border-radius: 3px;
}

.recommendation-reason p::-webkit-scrollbar-thumb:hover {
  background: #606266;
}

.card-actions {
  display: flex;
  gap: 8px;
}

.card-actions .el-button {
  flex: 1;
}

.confirm-section {
  max-width: 1200px;
  margin: 32px auto 0;
  text-align: center;
}

.confirm-button {
  width: 100%;
  max-width: 400px;
  height: 50px;
  font-size: 16px;
}

.loading-section {
  max-width: 1200px;
  margin: 0 auto;
}

.detail-content {
  padding: 16px 0;
}

.detail-description,
.detail-recommendation {
  margin-top: 24px;
}

.detail-description h4,
.detail-recommendation h4 {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}

.detail-description p,
.detail-recommendation p {
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
}

.feature-tag {
  margin-right: 8px;
  margin-bottom: 8px;
}

/* 响应式 */
@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    gap: 16px;
  }

  .page-title {
    font-size: 20px;
  }
}
</style>
