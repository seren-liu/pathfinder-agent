<template>
  <AppLayout>
    <div class="itinerary-editor">
      <!-- 加载状态 -->
      <div v-if="loading" class="loading-container">
        <el-skeleton :rows="10" animated />
      </div>

      <!-- 主编辑界面 -->
      <div v-else-if="trip && trip.days && trip.days.length > 0" class="editor-content">
        <!-- Header -->
        <div class="editor-header">
          <div class="header-left">
            <el-button @click="handleBack" :icon="ArrowLeft" circle />
            <div class="header-title">
              <h1>Edit Itinerary</h1>
              <p>{{ trip.destinationName }}, {{ trip.destinationCountry }}</p>
            </div>
          </div>
          <div class="header-actions">
            <el-button @click="handleOptimize">
              <el-icon><Star /></el-icon> AI Optimization Suggestions
            </el-button>
            <el-button type="primary" @click="handleSave">
              <el-icon><Document /></el-icon> Save Changes
            </el-button>
          </div>
        </div>

        <!-- AI Optimization Panel -->
        <el-card v-if="showOptimizePanel" class="optimize-panel" shadow="hover">
          <template #header>
            <div class="panel-header">
              <span><el-icon><Promotion /></el-icon> AI Optimization Suggestions</span>
              <el-button text @click="showOptimizePanel = false">Close</el-button>
            </div>
          </template>
          <div class="optimize-content">
            <p v-if="optimizationSuggestions.length === 0" class="ai-thinking">🤖 AI is analyzing your itinerary...</p>
            <el-alert
              v-for="(suggestion, index) in optimizationSuggestions"
              :key="`suggestion-${index}-${suggestion.id || suggestion.title?.substring(0, 20)}`"
              :title="suggestion.title || 'Optimization Suggestion'"
              :description="suggestion.description && suggestion.description !== suggestion.title ? suggestion.description : ''"
              type="info"
              show-icon
              :closable="false"
              style="margin-bottom: 10px"
            />
          </div>
        </el-card>

        <!-- 预算统计 -->
        <el-row :gutter="20" class="stats-row">
          <el-col :span="8">
            <el-card shadow="hover">
              <div class="stat-item">
                <el-icon :size="32" color="#67C23A"><Wallet /></el-icon>
                <div>
                  <p class="stat-label">Total Budget</p>
                  <p class="stat-value">${{ trip.totalBudget }}</p>
                </div>
              </div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="hover">
              <div class="stat-item">
                <el-icon :size="32" color="#409EFF"><Money /></el-icon>
                <div>
                  <p class="stat-label">Used</p>
                  <p class="stat-value">${{ actualCost }}</p>
                </div>
              </div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="hover">
              <div class="stat-item">
                <el-icon :size="32" :color="remainingBudgetColor">
                  <TrendCharts />
                </el-icon>
                <div>
                  <p class="stat-label">Remaining</p>
                  <p class="stat-value" :style="{ color: remainingBudgetColor }">
                    ${{ remainingBudget }}
                  </p>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>

        <!-- Add New Day Button -->
        <div class="add-day-section">
          <el-button type="primary" size="large" @click="handleAddNewDay">
            <el-icon><Plus /></el-icon> Add New Day
          </el-button>
        </div>

        <!-- 每日行程编辑 -->
        <div class="days-editor">
          <el-card
            v-for="day in trip.days"
            :key="day.dayNumber"
            class="day-editor-card"
            shadow="hover"
          >
            <template #header>
              <div class="day-header">
                <div class="day-info">
                  <h3>Day {{ day.dayNumber }}: {{ day.theme || 'Exploring' }}</h3>
                  <div class="day-date-section">
                    <el-date-picker
                      v-model="day.date"
                      type="date"
                      placeholder="Select date"
                      format="YYYY-MM-DD"
                      value-format="YYYY-MM-DD"
                      size="small"
                      @change="handleUpdateDayDate(day)"
                    />
                    <span v-if="!day.date" class="date-hint">Click to set date</span>
                    <span v-else class="date-display">{{ formatDate(day.date) }}</span>
                  </div>
                  <div class="day-meta">
                    <el-tag type="info" size="small">{{ day.activities?.length || 0 }} Activities</el-tag>
                    <el-tag type="success" size="small">${{ day.totalCost || 0 }}</el-tag>
                  </div>
                </div>
                <div class="day-header-actions">
                  <el-button type="primary" size="small" @click="handleAddActivity(day)">
                    <el-icon><Plus /></el-icon> Add Activity
                  </el-button>
                  <el-button type="danger" size="small" @click="handleDeleteDay(day)">
                    <el-icon><Delete /></el-icon> Delete Day
                  </el-button>
                </div>
              </div>
            </template>

            <!-- Activity list (draggable) -->
            <draggable
              v-model="day.activities"
              :item-key="(item) => item.activityId || item.id || item.itemId"
              handle=".drag-handle"
              @end="handleActivityMove"
            >
              <template #item="{ element: activity }">
                <div class="activity-editor-item">
                  <el-icon class="drag-handle"><Grid /></el-icon>
                  <div class="activity-content">
                    <div class="activity-name-time">
                      <span class="activity-time">{{ formatTime(activity.startTime) }}</span>
                      <span class="activity-name">{{ activity.activityName }}</span>
                    </div>
                    <div class="activity-meta">
                      <el-tag size="small">{{ activity.activityType }}</el-tag>
                      <span class="activity-cost">${{ activity.cost || 0 }}</span>
                    </div>
                  </div>
                  <div class="activity-actions">
                    <el-button text @click.stop="handleEditActivity(activity)">
                      <el-icon><Edit /></el-icon>
                    </el-button>
                    <el-button text type="danger" @click.stop="handleDeleteActivity(activity)">
                      <el-icon><Delete /></el-icon>
                    </el-button>
                  </div>
                </div>
              </template>
            </draggable>

            <el-empty v-if="!day.activities || day.activities.length === 0" description="No activities" />
          </el-card>
        </div>
      </div>

      <!-- Error state -->
      <el-empty v-else description="Trip not found" />

      <!-- Edit Activity Dialog -->
      <el-dialog
        v-model="editDialogVisible"
        title="Edit Activity"
        width="600px"
        @close="resetEditForm"
      >
        <el-form :model="editForm" label-width="120px">
          <el-form-item label="Activity Name" required>
            <el-input v-model="editForm.activityName" placeholder="Enter activity name" />
          </el-form-item>
          <el-form-item label="Activity Type">
            <el-select v-model="editForm.activityType" placeholder="Select activity type">
              <el-option label="Sightseeing" value="sightseeing" />
              <el-option label="Dining" value="dining" />
              <el-option label="Accommodation" value="accommodation" />
              <el-option label="Transportation" value="transportation" />
              <el-option label="Activity" value="activity" />
            </el-select>
          </el-form-item>
          <el-form-item label="Start Time">
            <el-time-picker
              v-model="editForm.startTime"
              format="HH:mm"
              value-format="HH:mm"
              placeholder="Select start time"
            />
          </el-form-item>
          <el-form-item label="Duration (minutes)">
            <el-input-number v-model="editForm.durationMinutes" :min="0" :max="1440" />
          </el-form-item>
          <el-form-item label="Location">
            <el-input v-model="editForm.location" placeholder="Enter location" />
          </el-form-item>
          <el-form-item label="Cost (AUD)">
            <el-input-number v-model="editForm.cost" :min="0" :precision="2" />
          </el-form-item>
          <el-form-item label="Notes">
            <el-input
              v-model="editForm.notes"
              type="textarea"
              :rows="3"
              placeholder="Enter notes"
            />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="editDialogVisible = false">Cancel</el-button>
          <el-button type="primary" @click="submitEdit">Save</el-button>
        </template>
      </el-dialog>

      <!-- Add New Day Dialog -->
      <el-dialog
        v-model="addDayDialogVisible"
        title="Add New Day"
        width="500px"
        @close="resetNewDayForm"
      >
        <el-form :model="newDayForm" label-width="120px">
          <el-form-item label="Day Number" required>
            <el-input-number 
              v-model="newDayForm.dayNumber" 
              :min="1" 
              :max="30"
              placeholder="Enter day number"
            />
          </el-form-item>
          <el-form-item label="Date">
            <el-date-picker
              v-model="newDayForm.date"
              type="date"
              placeholder="Select date"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
            />
          </el-form-item>
          <el-form-item label="Theme">
            <el-input 
              v-model="newDayForm.theme" 
              placeholder="e.g., Exploring, Relaxation"
            />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="addDayDialogVisible = false">Cancel</el-button>
          <el-button type="primary" @click="submitNewDay">Add Day</el-button>
        </template>
      </el-dialog>

      <!-- Add Activity Dialog -->
      <el-dialog
        v-model="addDialogVisible"
        title="Add Activity"
        width="600px"
        @close="resetAddForm"
      >
        <el-form :model="addForm" label-width="120px">
          <el-form-item label="Activity Name" required>
            <el-input v-model="addForm.activityName" placeholder="Enter activity name" />
          </el-form-item>
          <el-form-item label="Activity Type">
            <el-select v-model="addForm.activityType" placeholder="Select activity type">
              <el-option label="Sightseeing" value="sightseeing" />
              <el-option label="Dining" value="dining" />
              <el-option label="Accommodation" value="accommodation" />
              <el-option label="Transportation" value="transportation" />
              <el-option label="Activity" value="activity" />
            </el-select>
          </el-form-item>
          <el-form-item label="Start Time">
            <el-time-picker
              v-model="addForm.startTime"
              format="HH:mm"
              value-format="HH:mm"
              placeholder="Select start time"
            />
          </el-form-item>
          <el-form-item label="Duration (minutes)">
            <el-input-number v-model="addForm.durationMinutes" :min="0" :max="1440" />
          </el-form-item>
          <el-form-item label="Location">
            <el-input v-model="addForm.location" placeholder="Enter location" />
          </el-form-item>
          <el-form-item label="Cost (AUD)">
            <el-input-number v-model="addForm.cost" :min="0" :precision="2" />
          </el-form-item>
          <el-form-item label="Notes">
            <el-input
              v-model="addForm.notes"
              type="textarea"
              :rows="3"
              placeholder="Enter notes"
            />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="addDialogVisible = false">Cancel</el-button>
          <el-button type="primary" @click="submitAdd">Add</el-button>
        </template>
      </el-dialog>
    </div>
  </AppLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import draggable from 'vuedraggable'
import {
  ArrowLeft,
  Document,
  Wallet,
  Money,
  TrendCharts,
  Plus,
  Edit,
  Delete,
  Grid,
  Star,
  Promotion
} from '@element-plus/icons-vue'
import AppLayout from '@/layouts/AppLayout.vue'
import { getTripById } from '@/api/trips'
import { optimizeItinerary, saveItineraryEdit, deleteActivity, addActivity, updateActivity, addNewDay, updateDayDate, deleteDay } from '@/api/itinerary'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const trip = ref(null)
const showOptimizePanel = ref(false)
const optimizationSuggestions = ref([])
const editDialogVisible = ref(false)
const addDialogVisible = ref(false)
const addDayDialogVisible = ref(false)
const newDayForm = ref({
  dayNumber: null,
  date: null,
  theme: ''
})
const editForm = ref({
  activityId: null,
  dayId: null,
  activityName: '',
  activityType: 'activity',
  startTime: '09:00',
  durationMinutes: 60,
  location: '',
  cost: 0,
  notes: ''
})
const addForm = ref({
  dayId: null,
  dayNumber: null,
  activityName: '',
  activityType: 'activity',
  startTime: '09:00',
  durationMinutes: 60,
  location: '',
  cost: 0,
  notes: ''
})

// 计算属性
const actualCost = computed(() => {
  if (!trip.value || !trip.value.days) return 0
  return trip.value.days.reduce((sum, day) => sum + (day.totalCost || 0), 0)
})

const remainingBudget = computed(() => {
  return trip.value?.totalBudget - actualCost.value || 0
})

const remainingBudgetColor = computed(() => {
  const percentage = (remainingBudget.value / trip.value?.totalBudget) * 100
  if (percentage > 30) return '#67C23A'
  if (percentage > 10) return '#E6A23C'
  return '#F56C6C'
})

// 方法
const fetchTrip = async () => {
  try {
    loading.value = true
    const tripId = route.params.tripId || route.query.tripId
    
    console.log('ItineraryEditor - 开始加载行程, tripId:', tripId)
    console.log('route.params:', route.params)
    console.log('route.query:', route.query)
    
    if (!tripId) {
      console.error('❌ Trip ID缺失')
      ElMessage.error('行程ID是必需的')
      return
    }
    
    console.log('✅ 调用API获取行程...')
    // 添加时间戳参数避免缓存
    const tripData = await getTripById(tripId)
    console.log('✅ API响应:', tripData)
    console.log('📊 行程数据详情:', JSON.stringify(tripData, null, 2))
    
    if (tripData) {
      trip.value = tripData
      console.log('✅ 行程数据加载成功:', trip.value)
      
      // 对每个天的活动按时间排序
      if (trip.value.days && trip.value.days.length > 0) {
        trip.value.days.forEach(day => {
          if (day.activities && day.activities.length > 0) {
            day.activities.sort((a, b) => {
              const timeA = parseTime(a.startTime)
              const timeB = parseTime(b.startTime)
              return timeA - timeB
            })
          }
        })
        console.log('✅ 活动已按时间排序')
      }
      
      if (!trip.value.days || trip.value.days.length === 0) {
        console.warn('⚠️ 行程没有days数据')
        ElMessage.warning('Trip data incomplete, but you can continue editing')
        // 不返回，让用户看到空状态
      }
    } else {
      console.error('❌ API返回数据格式错误:', tripData)
      ElMessage.error('Failed to fetch trip data')
    }
  } catch (error) {
    console.error('❌ 获取行程失败:', error)
    console.error('错误详情:', error.response || error.message)
    ElMessage.error('Failed to load trip: ' + (error.message || 'Unknown error'))
  } finally {
    loading.value = false
    console.log('⏹️ 加载完成, loading:', loading.value, 'trip:', trip.value)
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

const formatTime = (time) => {
  if (!time) return '09:00'
  if (typeof time === 'string') {
    return time.length > 5 ? time.substring(0, 5) : time
  }
  return time.toString()
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

// 对指定天的活动进行排序
const sortActivitiesByTime = (day) => {
  if (day.activities && day.activities.length > 0) {
    day.activities.sort((a, b) => {
      const timeA = parseTime(a.startTime)
      const timeB = parseTime(b.startTime)
      return timeA - timeB
    })
  }
}

const handleBack = () => {
  router.push({
    name: 'ItineraryOverview',
    query: { tripId: route.params.tripId }
  })
}

const handleOptimize = async () => {
  try {
    showOptimizePanel.value = true
    optimizationSuggestions.value = [] // 清空之前的建议
    
    // 异步调用，不阻塞UI
    optimizeItinerary(route.params.tripId, {
      optimizationType: 'general'
    }).then(data => {
      console.log('✅ AI优化响应:', data)
      
      // 解析响应数据
      if (data) {
        // 如果返回的是OptimizeResponse格式
        if (data.changes && data.changes.length > 0) {
          // 前端二次去重（防止后端遗漏，更严格的去重）
          const seen = new Set()
          const tempSuggestions = []
          
          data.changes.forEach((change, idx) => {
            const reason = (change.reason || '').trim()
            if (!reason) return
            
            // 更强的标准化：小写、去除所有空格和标点符号
            const normalized = reason
              .toLowerCase()
              .replace(/[^\w\s]/g, '')  // 移除所有标点符号
              .replace(/\s+/g, ' ')      // 多个空格合并为一个
              .trim()
            
            // 检查是否重复
            if (!normalized || seen.has(normalized)) {
              console.log('跳过重复建议:', reason.substring(0, 50))
              return
            }
            
            seen.add(normalized)
            
            // 提取标题和描述（如果有冒号分隔）
            let title = reason
            let description = ''
            
            // 如果有冒号，尝试分离标题和描述
            const colonIndex = reason.indexOf(':')
            if (colonIndex > 0 && colonIndex < 80) {
              title = reason.substring(0, colonIndex).trim()
              description = reason.substring(colonIndex + 1).trim()
            } else {
              // 如果没有明显分隔，标题就是完整内容，描述为空
              title = reason
              description = ''
            }
            
            tempSuggestions.push({
              id: idx,
              title: title,
              description: description
            })
          })
          
          optimizationSuggestions.value = tempSuggestions
          console.log('✅ 处理后的建议数量:', optimizationSuggestions.value.length)
        } else if (data.suggestions && data.suggestions.length > 0) {
          // 对suggestions也去重
          const seen = new Set()
          optimizationSuggestions.value = data.suggestions.filter(s => {
            const key = (s.title || s.description || '').trim().toLowerCase().replace(/[^\w\s]/g, '').replace(/\s+/g, ' ')
            if (seen.has(key)) return false
            seen.add(key)
            return true
          })
        } else if (data.aiExplanation) {
          optimizationSuggestions.value = [{
            id: 0,
            title: 'AI Analysis',
            description: data.aiExplanation
          }]
        }
      }
      
      if (optimizationSuggestions.value.length === 0) {
        optimizationSuggestions.value = [{
          title: 'No optimization needed',
          description: 'Your itinerary is well-balanced. No major changes suggested.'
        }]
      }
      ElMessage.success('AI analysis completed!')
    }).catch(error => {
      console.error('AI优化失败:', error)
      optimizationSuggestions.value = [{
        title: 'Optimization unavailable',
        description: 'AI optimization service is temporarily unavailable. Your itinerary looks good as is.'
      }]
      ElMessage.warning('AI optimization is taking longer than expected')
    })
  } catch (error) {
    console.error('AI优化失败:', error)
    ElMessage.error('AI analysis failed')
  }
}

const handleSave = async () => {
  try {
    loading.value = true
    
    await saveItineraryEdit(route.params.tripId, 'User edited itinerary')
    ElMessage.success('Itinerary saved successfully')
    // 重新加载行程数据以显示最新更改
    await fetchTrip()
    // 等待1秒后返回总览页面
    setTimeout(() => {
      handleBack()
    }, 1000)
  } catch (error) {
    console.error('保存失败:', error)
    ElMessage.error('Failed to save: ' + (error.message || 'Unknown error'))
  } finally {
    loading.value = false
  }
}

const handleActivityMove = () => {
  console.log('活动已移动')
}

const handleAddActivity = (day) => {
  console.log('🟢 添加活动按钮被点击，Day数据:', day)
  
  // 填充添加表单
  // 尝试多种可能的ID字段
  const dayId = day.dayId || day.id || (day.dayNumber ? findDayIdByNumber(day.dayNumber) : null)
  
  if (!dayId) {
    ElMessage.error('Cannot get day ID, please refresh and try again')
    console.error('❌ Day ID缺失:', day)
    console.error('可用的day字段:', Object.keys(day))
    return
  }
  
  addForm.value = {
    dayId: dayId,
    dayNumber: day.dayNumber,
    activityName: '',
    activityType: 'activity',
    startTime: '09:00',
    durationMinutes: 60,
    location: '',
    cost: 0,
    notes: ''
  }
  
  console.log('✅ 添加表单已填充:', addForm.value)
  addDialogVisible.value = true
  console.log('✅ 添加对话框已打开')
}

// 通过dayNumber查找dayId的辅助函数
const findDayIdByNumber = (dayNumber) => {
  if (!trip.value || !trip.value.days) return null
  const day = trip.value.days.find(d => d.dayNumber === dayNumber)
  return day ? (day.dayId || day.id) : null
}

// 获取天的ID（统一处理）
const getDayId = (day) => {
  return day.dayId || day.id || null
}

const resetAddForm = () => {
  addForm.value = {
    dayId: null,
    dayNumber: null,
    activityName: '',
    activityType: 'activity',
    startTime: '09:00',
    durationMinutes: 60,
    location: '',
    cost: 0,
    notes: ''
  }
}

const submitAdd = async () => {
  if (!addForm.value.activityName.trim()) {
    ElMessage.warning('Please enter activity name')
    return
  }
  
  if (!addForm.value.dayId) {
    ElMessage.error('Day ID is missing. Please refresh the page and try again.')
    console.error('Day ID缺失:', addForm.value)
    return
  }

  try {
    loading.value = true
    console.log('📤 添加活动请求:', {
      tripId: route.params.tripId,
      dayId: addForm.value.dayId,
      activityName: addForm.value.activityName
    })
    
    const createdActivity = await addActivity(route.params.tripId, {
      dayId: addForm.value.dayId,
      activityName: addForm.value.activityName,
      activityType: addForm.value.activityType,
      startTime: addForm.value.startTime,
      durationMinutes: addForm.value.durationMinutes,
      location: addForm.value.location,
      cost: addForm.value.cost,
      notes: addForm.value.notes
    })
    
    console.log('✅ 添加活动响应:', createdActivity)
    ElMessage.success('Activity added successfully!')
    addDialogVisible.value = false
    
    // 强制刷新：延迟一小段时间确保后端更新完成，然后重新加载数据
    await new Promise(resolve => setTimeout(resolve, 300))
    await fetchTrip()
    
    console.log('🔄 数据已刷新，新活动已添加并已按时间排序')
  } catch (error) {
    console.error('添加活动失败:', error)
    console.error('错误详情:', error.response || error.message)
    ElMessage.error('Failed to add activity: ' + (error.response?.data?.message || error.message || 'Unknown error'))
  } finally {
    loading.value = false
  }
}

const handleEditActivity = (activity) => {
  console.log('🟢 编辑按钮被点击，活动数据:', activity)
  
  // 填充编辑表单
  // 确保使用正确的ID字段（可能是id或activityId）
  const activityId = activity.activityId || activity.id || activity.itemId
  console.log('🔍 解析的activityId:', activityId)
  
  if (!activityId) {
    ElMessage.error('Cannot get activity ID, please refresh and try again')
    console.error('❌ 活动ID缺失:', activity)
    return
  }
  
  editForm.value = {
    activityId: activityId,
    dayId: activity.dayId || null,
    activityName: activity.activityName || '',
    activityType: activity.activityType || 'activity',
    startTime: (activity.startTime && typeof activity.startTime === 'string') ? activity.startTime : 
               (activity.startTime ? activity.startTime.toString().substring(0, 5) : '09:00'),
    durationMinutes: activity.durationMinutes || activity.duration || 60,
    location: activity.location || '',
    cost: activity.cost || 0,
    notes: activity.notes || ''
  }
  
  console.log('✅ 编辑表单已填充:', editForm.value)
  editDialogVisible.value = true
  console.log('✅ 编辑对话框已打开')
}

const resetEditForm = () => {
  editForm.value = {
    activityId: null,
    dayId: null,
    activityName: '',
    activityType: 'activity',
    startTime: '09:00',
    durationMinutes: 60,
    location: '',
    cost: 0,
    notes: ''
  }
}

const submitEdit = async () => {
  if (!editForm.value.activityName.trim()) {
    ElMessage.warning('Please enter activity name')
    return
  }
  
  if (!editForm.value.activityId) {
    ElMessage.error('Activity ID is missing. Please refresh the page and try again.')
    console.error('Activity ID缺失:', editForm.value)
    return
  }

  try {
    loading.value = true
    console.log('📤 更新活动请求:', {
      tripId: route.params.tripId,
      activityId: editForm.value.activityId,
      activityName: editForm.value.activityName
    })
    
    const updatedActivity = await updateActivity(route.params.tripId, editForm.value.activityId, {
      activityName: editForm.value.activityName,
      activityType: editForm.value.activityType,
      startTime: editForm.value.startTime,
      durationMinutes: editForm.value.durationMinutes,
      location: editForm.value.location,
      cost: editForm.value.cost,
      notes: editForm.value.notes
    })
    
    console.log('✅ 更新活动响应:', updatedActivity)
    ElMessage.success('Activity updated successfully!')
    editDialogVisible.value = false
    
    // 强制刷新：延迟一小段时间确保后端更新完成，然后重新加载数据
    await new Promise(resolve => setTimeout(resolve, 300))
    await fetchTrip()
    
    // 数据加载后会自动排序（在fetchTrip中处理）
    console.log('🔄 数据已刷新，活动已按时间排序')
  } catch (error) {
    console.error('更新活动失败:', error)
    console.error('错误详情:', error.response || error.message)
    ElMessage.error('Failed to update activity: ' + (error.response?.data?.message || error.message || 'Unknown error'))
  } finally {
    loading.value = false
  }
}

const handleDeleteActivity = async (activity) => {
  try {
    // 确保获取正确的活动ID
    const activityId = activity.activityId || activity.id || activity.itemId
    
    if (!activityId) {
      ElMessage.error('Cannot get activity ID')
      console.error('活动ID缺失:', activity)
      return
    }
    
    await deleteActivity(route.params.tripId, activityId)
    ElMessage.success(`Deleted: ${activity.activityName}`)
    // 重新加载行程以显示最新更改
    await fetchTrip()
  } catch (error) {
    console.error('删除失败:', error)
    ElMessage.error('Failed to delete activity: ' + (error.message || 'Unknown error'))
  }
}

const handleAddNewDay = () => {
  // 自动计算下一个天数
  const maxDayNumber = trip.value.days && trip.value.days.length > 0
    ? Math.max(...trip.value.days.map(d => d.dayNumber || 0))
    : 0
  
  newDayForm.value = {
    dayNumber: maxDayNumber + 1,
    date: null,
    theme: ''
  }
  addDayDialogVisible.value = true
}

const resetNewDayForm = () => {
  newDayForm.value = {
    dayNumber: null,
    date: null,
    theme: ''
  }
}

const submitNewDay = async () => {
  if (!newDayForm.value.dayNumber) {
    ElMessage.warning('Please enter day number')
    return
  }
  
  try {
    loading.value = true
    const tripId = route.params.tripId
    console.log('提交新天 - tripId:', tripId, 'data:', newDayForm.value)
    
    const newDay = await addNewDay(tripId, {
      dayNumber: newDayForm.value.dayNumber,
      date: newDayForm.value.date || null,
      theme: newDayForm.value.theme || ''
    })
    
    console.log('添加新天响应:', newDay)
    ElMessage.success('New day added successfully!')
    addDayDialogVisible.value = false
    resetNewDayForm()
    // 重新加载行程数据
    await fetchTrip()
  } catch (error) {
    console.error('添加新天失败:', error)
    const errorMsg = error.response?.data?.message || error.response?.data?.error || error.message || 'Unknown error'
    ElMessage.error('Failed to add new day: ' + errorMsg)
  } finally {
    loading.value = false
  }
}

const handleUpdateDayDate = async (day) => {
  try {
    console.log('更新天的日期 - day:', day, 'date:', day.date)
    const dayId = day.dayId || day.id
    if (!dayId) {
      ElMessage.error('Cannot get day ID')
      return
    }
    await updateDayDate(route.params.tripId, dayId, day.date)
    ElMessage.success('Date updated successfully')
  } catch (error) {
    console.error('更新日期失败:', error)
    ElMessage.error('Failed to update date: ' + (error.response?.data?.message || error.message || 'Unknown error'))
  }
}

const handleDeleteDay = async (day) => {
  try {
    await ElMessageBox.confirm(
      `Are you sure you want to delete Day ${day.dayNumber}? This will also delete all activities on this day.`,
      'Delete Day',
      {
        confirmButtonText: 'Delete',
        cancelButtonText: 'Cancel',
        type: 'warning'
      }
    )
    
    loading.value = true
    const tripId = route.params.tripId
    
    // day对象应该有dayId字段（从后端ItineraryDayResponse返回）
    const dayId = day.dayId || day.id
    
    console.log('删除天 - tripId:', tripId, 'dayId:', dayId, 'day对象:', day)
    
    if (!dayId) {
      ElMessage.error('Cannot get day ID. Day object: ' + JSON.stringify(day))
      console.error('天ID缺失 - day对象:', day)
      loading.value = false
      return
    }
    
    console.log('调用deleteDay API - tripId:', tripId, 'dayId:', dayId)
    const deletedDay = await deleteDay(tripId, dayId)
    console.log('删除天的响应:', deletedDay)
    
    ElMessage.success(`Day ${day.dayNumber} deleted successfully`)
    // 重新加载行程数据
    await fetchTrip()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除天失败:', error)
      ElMessage.error('Failed to delete day: ' + (error.response?.data?.message || error.message || 'Unknown error'))
    }
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchTrip()
})
</script>

<style scoped>
.itinerary-editor {
  max-width: 1400px;
  margin: 0 auto;
}

.loading-container {
  padding: 40px;
}

.editor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
  padding-bottom: 20px;
  border-bottom: 2px solid #f0f0f0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 20px;
}

.header-title h1 {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 5px 0;
}

.header-title p {
  font-size: 14px;
  color: #909399;
  margin: 0;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.stats-row {
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

.add-day-section {
  margin-bottom: 30px;
  text-align: center;
}

.optimize-panel {
  margin-bottom: 30px;
  background: linear-gradient(135deg, #667eea15 0%, #764ba215 100%);
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.ai-thinking {
  color: #606266;
  font-size: 14px;
}

.days-editor {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.day-editor-card {
  transition: transform 0.3s, box-shadow 0.3s;
}

.day-editor-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
}

.day-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 15px;
}

.day-header-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.day-info h3 {
  margin: 0 0 5px 0;
  font-size: 18px;
  color: #303133;
}

.day-date-section {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 8px 0;
}

.day-date-section .date-hint {
  font-size: 12px;
  color: #c0c4cc;
  font-style: italic;
}

.day-date-section .date-display {
  font-size: 14px;
  color: #909399;
}

.day-date {
  margin: 0 0 10px 0;
  font-size: 14px;
  color: #909399;
}

.day-meta {
  display: flex;
  gap: 8px;
}

.activity-editor-item {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 15px;
  background: #f5f7fa;
  border-radius: 8px;
  margin-bottom: 10px;
  transition: all 0.3s;
}

.activity-editor-item:hover {
  background: #ecf5ff;
  transform: translateX(5px);
}

.drag-handle {
  cursor: move;
  color: #909399;
  font-size: 20px;
}

.activity-content {
  flex: 1;
}

.activity-name-time {
  display: flex;
  gap: 15px;
  margin-bottom: 8px;
}

.activity-time {
  font-weight: 600;
  color: #409EFF;
  min-width: 70px;
}

.activity-name {
  font-weight: 500;
  color: #303133;
}

.activity-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.activity-cost {
  font-weight: 600;
  color: #67C23A;
}

.activity-actions {
  display: flex;
  gap: 5px;
}
</style>
