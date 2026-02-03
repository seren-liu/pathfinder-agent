<template>
  <AppLayout>
    <div class="intent-container">
    <div class="intent-card">
      <!-- Header -->
      <div v-if="chatMessages.length === 0" class="card-header">
        <el-icon class="header-icon" :size="48"><Compass /></el-icon>
        <h2 class="header-title">Where would you like to travel?</h2>
        <p class="header-subtitle">Tell me about your dream trip...</p>
      </div>

      <!-- Chat History -->
      <div v-if="chatMessages.length > 0" class="chat-history">
        <div v-for="(msg, index) in chatMessages" :key="index" :class="['chat-message', msg.role]">
          <div class="message-avatar">
            <el-icon v-if="msg.role === 'user'" :size="20"><User /></el-icon>
            <el-icon v-else :size="20"><ChatDotRound /></el-icon>
          </div>
          <div class="message-bubble">
            <div class="message-content">{{ msg.content }}</div>
            <div class="message-time">{{ formatTime(msg.timestamp) }}</div>
          </div>
        </div>
        <div v-if="chatLoading" class="chat-message assistant">
          <div class="message-avatar">
            <el-icon :size="20"><ChatDotRound /></el-icon>
          </div>
          <div class="message-bubble typing">
            <span class="typing-dot"></span>
            <span class="typing-dot"></span>
            <span class="typing-dot"></span>
          </div>
        </div>
      </div>

      <!-- Input Area -->
      <el-form class="intent-form">
        <el-form-item>
          <el-input
            v-model="userInput"
            :type="chatMessages.length > 0 ? 'text' : 'textarea'"
            :rows="chatMessages.length > 0 ? 1 : 6"
            :placeholder="chatMessages.length > 0 ? 'Continue the conversation...' : 'E.g., I need to relax after a stressful semester, looking for a beach with mountains nearby, budget around $2000...'"
            maxlength="500"
            :show-word-limit="chatMessages.length === 0"
            @keyup.enter="chatMessages.length > 0 ? sendChatMessage() : null"
            @keyup.enter.ctrl="chatMessages.length === 0 ? handleStartChat() : null"
          >
            <template v-if="chatMessages.length > 0" #append>
              <el-button :icon="Position" @click="sendChatMessage" :loading="chatLoading" />
            </template>
          </el-input>
        </el-form-item>

        <!-- AI Auto-Suggestions (显示解析结果) -->
        <div v-if="parsedIntent" class="ai-suggestions">
          <h3 class="suggestions-title">
            <el-icon><MagicStick /></el-icon>
            AI Auto-Suggestions
          </h3>
          
          <el-row :gutter="16">
            <el-col :span="8">
              <el-card shadow="hover">
                <template #header>
                  <el-icon><Money /></el-icon> Budget
                </template>
                <div class="suggestion-content">
                  <el-tag :type="getBudgetType(parsedIntent.budgetLevel)" size="large">
                    {{ getBudgetLabel(parsedIntent.budgetLevel) }}
                  </el-tag>
                  <p class="suggestion-detail">
                    ${{ estimateBudget(parsedIntent.budgetLevel, parsedIntent.estimatedDuration) }}
                  </p>
                </div>
              </el-card>
            </el-col>

            <el-col :span="8">
              <el-card shadow="hover">
                <template #header>
                  <el-icon><Calendar /></el-icon> Duration
                </template>
                <div class="suggestion-content">
                  <el-tag type="info" size="large">
                    {{ parsedIntent.estimatedDuration }} days
                  </el-tag>
                  <p class="suggestion-detail">Recommended trip length</p>
                </div>
              </el-card>
            </el-col>

            <el-col :span="8">
              <el-card shadow="hover">
                <template #header>
                  <el-icon><Sunny /></el-icon> Mood
                </template>
                <div class="suggestion-content">
                  <el-tag type="warning" size="large">
                    {{ parsedIntent.mood }}
                  </el-tag>
                  <p class="suggestion-detail">Your travel vibe</p>
                </div>
              </el-card>
            </el-col>
          </el-row>

          <!-- Preferred Features -->
          <div class="features-section">
            <h4>Preferred Features:</h4>
            <el-tag
              v-for="feature in parsedIntent.preferredFeatures"
              :key="feature"
              class="feature-tag"
              effect="plain"
            >
              {{ feature }}
            </el-tag>
          </div>
        </div>

        <!-- Action Buttons -->
        <div class="action-buttons">
          <el-button
            v-if="chatMessages.length === 0"
            type="primary"
            size="large"
            class="continue-button"
            :loading="chatLoading"
            :disabled="!userInput.trim()"
            @click="handleStartChat"
          >
            <el-icon v-if="!chatLoading"><ChatDotRound /></el-icon>
            {{ chatLoading ? 'AI is thinking...' : 'Start Conversation' }}
          </el-button>
          
          <template v-else>
            <!-- 手动触发推荐按钮（优先级更高：目的地不清晰时显示） -->
            <el-button
              v-if="currentIntent && currentIntent.needsRecommendation && !currentIntent.readyForItinerary && currentPhase === 'chat'"
              type="success"
              size="large"
              class="generate-button"
              :loading="loading"
              @click="handleGetRecommendations"
            >
              <el-icon><Search /></el-icon>
              Get Recommendations
            </el-button>
            
            <!-- 手动触发生成行程按钮（仅在目的地清晰时显示） -->
            <el-button
              v-else-if="currentIntent && currentIntent.readyForItinerary && !currentIntent.needsRecommendation && currentPhase === 'chat'"
              type="primary"
              size="large"
              class="generate-button"
              :loading="loading"
              @click="handleGenerateItinerary"
            >
              <el-icon><Calendar /></el-icon>
              Generate Itinerary
            </el-button>
            
          </template>
        </div>
      </el-form>

      <!-- Timeout Warning -->
      <el-alert
        v-if="showSlowWarning"
        type="warning"
        :closable="false"
        class="warning-alert"
      >
        AI is taking longer than usual. Please wait or try with a simpler description.
      </el-alert>
    </div>
  </div>
  </AppLayout>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Compass, MagicStick, Money, Calendar, Sunny, Search, ChatDotRound, User, Position, RefreshLeft } from '@element-plus/icons-vue'
import { chatWithAgent } from '@/api/agent'
import { clearChatHistory } from '@/api/chat'
import { recommendDestinationsApi } from '@/api/ai'
import { generateItinerary as generateItineraryApi } from '@/api/trips'
import { useUserStore } from '@/stores/user'
import AppLayout from '@/layouts/AppLayout.vue'

const router = useRouter()
const userStore = useUserStore()

// ========== 表单数据 ==========
const userInput = ref('')
const parsedIntent = ref(null)
const loading = ref(false)
const showSlowWarning = ref(false)

// ========== 多轮对话数据 ==========
const chatMessages = ref([])
const sessionId = ref(null)
const chatLoading = ref(false)
const currentIntent = ref(null)
const currentPhase = ref('chat')
const agentResponse = ref(null)

// ========== 多轮对话功能 ==========
const handleStartChat = async () => {
  if (!userInput.value.trim()) {
    ElMessage.warning('Please describe your travel plans')
    return
  }

  if (!userStore.userId) {
    ElMessage.error('Please login first')
    router.push('/login')
    return
  }

  await sendChatMessage()
}

const sendChatMessage = async () => {
  if (!userInput.value.trim() || chatLoading.value) return

  const userMessage = userInput.value.trim()
  
  chatMessages.value.push({
    role: 'user',
    content: userMessage,
    timestamp: new Date()
  })

  userInput.value = ''
  chatLoading.value = true

  try {
    if (!sessionId.value) {
      sessionId.value = `session-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`
    }

    const response = await chatWithAgent({
      userId: userStore.userId,
      sessionId: sessionId.value,
      message: userMessage
    })
    
    agentResponse.value = response
    
    handleAgentResponse(response)

    await nextTick()
    scrollToBottom()

  } catch (error) {
    console.error('Agent chat failed:', error)
    ElMessage.error(error.response?.data?.message || 'Failed to send message')
    chatMessages.value.pop()
  } finally {
    chatLoading.value = false
  }
}

const handleAgentResponse = (response) => {
  console.log('🤖 Agent Response:', response)
  
  if (response.intent) {
    currentIntent.value = response.intent
  }
  
  switch (response.actionType) {
    case 'chat':
      chatMessages.value.push({
        role: 'assistant',
        content: response.message,
        timestamp: new Date()
      })
      break
      
    case 'recommend':
      // Agent 决定推荐 → 跳转到 Destinations 页面
      ElMessage.success('🎯 Agent is finding destinations for you!')
      
      // 跳转到推荐页面，携带意图信息（包括目的地偏好）
      router.push({
        name: 'Destinations',
        query: {
          sessionId: sessionId.value,
          destination: currentIntent.value?.destination || '',  // 添加目的地
          interests: currentIntent.value?.interests?.join(',') || '',
          mood: currentIntent.value?.mood || '',
          budget: currentIntent.value?.budget || '',
          days: currentIntent.value?.days || ''
        }
      })
      break
      
    case 'generate':
      if (response.tripId) {
        ElMessage.success('🚀 Itinerary generation started!')
        router.push({
          name: 'ItineraryProgress',
          params: { tripId: response.tripId }
        })
      }
      break
      
    case 'complete':
      chatMessages.value.push({
        role: 'assistant',
        content: response.message || 'Task completed!',
        timestamp: new Date()
      })
      currentPhase.value = 'chat'
      break
      
    default:
      chatMessages.value.push({
        role: 'assistant',
        content: response.message || 'Processing...',
        timestamp: new Date()
      })
  }
}


const handleGetRecommendations = () => {
  if (!currentIntent.value) {
    ElMessage.warning('Please chat first to help me understand your preferences')
    return
  }
  
  if (!sessionId.value) {
    ElMessage.error('Session not initialized. Please start a conversation first.')
    return
  }
  
  console.log('Routing to Destinations with:', {
    sessionId: sessionId.value,
    intent: currentIntent.value
  })
  
  // 跳转到推荐页面，携带意图信息（包括目的地偏好）
  router.push({
    name: 'Destinations',
    query: {
      sessionId: sessionId.value,
      destination: currentIntent.value.destination || '',  // 添加目的地
      interests: currentIntent.value.interests?.join(',') || 'beach',
      mood: currentIntent.value.mood || 'relaxing',
      budget: currentIntent.value.budget || '10000',
      days: String(currentIntent.value.days || 5)
    }
  })
}

const handleGenerateItinerary = async () => {
  if (!currentIntent.value) {
    ElMessage.warning('Please specify your travel plans first')
    return
  }
  
  if (!currentIntent.value.destination) {
    ElMessage.warning('Please specify a destination first')
    return
  }
  
  loading.value = true
  
  try {
    const tripId = await generateItineraryApi({
      userId: userStore.userId,
      destinationName: currentIntent.value.destination,
      destinationCountry: currentIntent.value.country || 'Unknown',
      durationDays: currentIntent.value.days || 5,
      totalBudget: parseFloat(currentIntent.value.budget) || 10000,
      partySize: 1,
      sessionId: sessionId.value
    })
    
    ElMessage.success('🚀 Itinerary generation started!')
    
    router.push({
      name: 'ItineraryProgress',
      params: { tripId }
    })
    
  } catch (error) {
    console.error('Failed to generate itinerary:', error)
    ElMessage.error('Failed to generate itinerary')
  } finally {
    loading.value = false
  }
}

const handleClearChat = async () => {
  try {
    await ElMessageBox.confirm(
      'This will clear the conversation history. Continue?',
      'Start Over',
      {
        confirmButtonText: 'Yes',
        cancelButtonText: 'Cancel',
        type: 'warning'
      }
    )

    if (sessionId.value) {
      await clearChatHistory(sessionId.value, userStore.userId)
    }

    chatMessages.value = []
    sessionId.value = null
    userInput.value = ''
    parsedIntent.value = null
    currentIntent.value = null
    agentResponse.value = null
    
    ElMessage.success('Conversation cleared')
  } catch (error) {
    // 用户取消
  }
}

const scrollToBottom = () => {
  const chatHistory = document.querySelector('.chat-history')
  if (chatHistory) {
    chatHistory.scrollTop = chatHistory.scrollHeight
  }
}

const formatTime = (timestamp) => {
  const date = new Date(timestamp)
  return date.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' })
}

const getBudgetType = (level) => {
  return { 1: 'success', 2: 'warning', 3: 'danger' }[level] || 'info'
}

const getBudgetLabel = (level) => {
  return { 1: 'Budget ($)', 2: 'Moderate ($$)', 3: 'Luxury ($$$)' }[level] || 'Unknown'
}

const estimateBudget = (level, days) => {
  const dailyCost = { 1: 150, 2: 300, 3: 500 }[level] || 300
  return (dailyCost * (days || 5)).toLocaleString()
}
</script>

<style scoped>
.intent-container {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 20px;
}

.intent-card {
  width: 100%;
  max-width: 900px;
  padding: 48px;
  min-height: 600px;
  display: flex;
  flex-direction: column;
}

.card-header {
  text-align: center;
  margin-bottom: 36px;
}

.header-icon {
  color: #667eea;
  margin-bottom: 16px;
}

.header-title {
  font-size: 32px;
  font-weight: 700;
  color: #303133;
  margin-bottom: 8px;
}

.header-subtitle {
  font-size: 16px;
  color: #909399;
}

.intent-form {
  margin-top: 24px;
}

.continue-button {
  width: 100%;
  margin-top: 24px;
  height: 50px;
  font-size: 16px;
  font-weight: 600;
}

.ai-suggestions {
  margin: 32px 0;
  padding: 24px;
  background: #f5f7fa;
  border-radius: 12px;
}

.suggestions-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.suggestion-content {
  text-align: center;
  padding: 12px 0;
}

.suggestion-detail {
  margin-top: 12px;
  font-size: 14px;
  color: #606266;
}

.features-section {
  margin-top: 24px;
}

.features-section h4 {
  font-size: 14px;
  color: #606266;
  margin-bottom: 12px;
}

.feature-tag {
  margin-right: 8px;
  margin-bottom: 8px;
}

.warning-alert {
  margin-top: 16px;
}

/* 聊天历史样式 */
.chat-history {
  flex: 1;
  max-height: 400px;
  overflow-y: auto;
  margin: 24px 0;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 12px;
}

.chat-message {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  animation: slideIn 0.3s ease-out;
}

.chat-message.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.chat-message.user .message-avatar {
  background: #667eea;
  color: white;
}

.chat-message.assistant .message-avatar {
  background: #48bb78;
  color: white;
}

.message-bubble {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 12px;
  background: white;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

.chat-message.user .message-bubble {
  background: #667eea;
  color: white;
  border-bottom-right-radius: 4px;
}

.chat-message.assistant .message-bubble {
  background: white;
  border-bottom-left-radius: 4px;
}

.message-content {
  font-size: 14px;
  line-height: 1.6;
  word-wrap: break-word;
}

.message-time {
  font-size: 11px;
  margin-top: 4px;
  opacity: 0.7;
}

.chat-message.user .message-time {
  text-align: right;
}

.message-bubble.typing {
  display: flex;
  gap: 4px;
  padding: 16px;
}

.typing-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #909399;
  animation: typing 1.4s infinite;
}

.typing-dot:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing {
  0%, 60%, 100% {
    transform: translateY(0);
  }
  30% {
    transform: translateY(-10px);
  }
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.action-buttons {
  display: flex;
  gap: 12px;
  margin-top: 24px;
}

.generate-button {
  flex: 1;
  height: 50px;
  font-size: 16px;
  font-weight: 600;
}

/* 响应式 */
@media (max-width: 768px) {
  .intent-card {
    padding: 32px 24px;
  }

  .header-title {
    font-size: 24px;
  }
}
</style>
