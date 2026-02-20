<template>
  <AppLayout>
    <section class="intent-page">
      <div class="intent-workspace">
        <aside :class="['session-sidebar', { 'is-collapsed': isSidebarCollapsed }]">
          <div class="sidebar-header">
            <p v-if="!isSidebarCollapsed" class="sidebar-title">Conversations</p>
            <el-button
              class="collapse-btn"
              text
              circle
              :icon="isSidebarCollapsed ? ArrowRightBold : ArrowLeftBold"
              @click="isSidebarCollapsed = !isSidebarCollapsed"
            />
          </div>

          <div v-if="!isSidebarCollapsed" class="sidebar-actions">
            <el-button type="primary" plain size="small" @click="handleNewConversation">
              New
            </el-button>
          </div>

          <el-scrollbar v-if="!isSidebarCollapsed" class="session-list">
            <button
              v-for="session in conversationSessions"
              :key="session.id"
              type="button"
              :class="['session-item', { 'is-active': session.id === activeSessionId }]"
              @click="handleSelectConversation(session.id)"
            >
              <p class="session-item-title">{{ session.title }}</p>
              <p class="session-item-preview">{{ session.lastMessage || "No messages yet" }}</p>
              <p class="session-item-time">{{ formatSessionTime(session.updatedAt) }}</p>
            </button>

            <div v-if="conversationSessions.length === 0" class="session-empty">
              No conversations yet
            </div>
          </el-scrollbar>
        </aside>

        <div class="chat-center">
          <el-card class="intent-card" shadow="never">
        <div class="intent-toolbar">
          <div class="toolbar-left">
            <span class="status-dot" aria-hidden="true"></span>
            <span class="toolbar-label">Intent Chat</span>
          </div>

          <el-button
            v-if="chatMessages.length > 0"
            type="danger"
            plain
            :icon="Delete"
            class="toolbar-button"
            @click="handleClearChat"
          >
            Clear
          </el-button>
        </div>

        <el-scrollbar class="chat-history">
          <div v-if="chatMessages.length === 0" class="empty-state">
            <p class="empty-kicker">Start planning with one sentence</p>
            <div class="quick-prompts">
              <el-button
                v-for="prompt in quickPrompts"
                :key="prompt"
                text
                bg
                round
                class="quick-prompt-button"
                @click="applyQuickPrompt(prompt)"
              >
                {{ prompt }}
              </el-button>
            </div>
          </div>

          <div
            v-for="(msg, index) in chatMessages"
            :key="index"
            :class="['chat-message', msg.role]"
          >
            <div class="message-avatar">
              <el-icon v-if="msg.role === 'user'" :size="18"><User /></el-icon>
              <el-icon v-else :size="18"><ChatDotRound /></el-icon>
            </div>
            <div class="message-bubble">
              <div class="message-content">{{ msg.content }}</div>
              <div class="message-time">{{ formatTime(msg.timestamp) }}</div>
            </div>
          </div>

          <div v-if="chatLoading" class="chat-message assistant">
            <div class="message-avatar">
              <el-icon :size="18"><ChatDotRound /></el-icon>
            </div>
            <div class="message-bubble typing" aria-live="polite">
              <span class="typing-dot"></span>
              <span class="typing-dot"></span>
              <span class="typing-dot"></span>
            </div>
          </div>
        </el-scrollbar>
          </el-card>
        </div>
      </div>

      <section class="page-composer" aria-label="Message composer">
        <div v-if="parsedIntent || nextAction" class="assistant-rail">
          <div v-if="parsedIntent" class="intent-snapshot">
            <span class="snapshot-chip">{{ getBudgetLabel(parsedIntent.budgetLevel) }}</span>
            <span class="snapshot-chip">{{ parsedIntent.estimatedDuration }} days</span>
            <span class="snapshot-chip">{{ parsedIntent.mood }}</span>
          </div>

          <div v-if="nextAction" class="next-action-row">
            <p class="input-tip" role="status">{{ nextAction.hint }}</p>
            <el-button
              :type="nextAction.type"
              class="next-action-button"
              :loading="loading"
              @click="handleNextAction"
            >
              <el-icon v-if="nextAction.kind === 'recommend'"><Search /></el-icon>
              <el-icon v-else><Calendar /></el-icon>
              {{ nextAction.label }}
            </el-button>
          </div>
        </div>

        <el-form class="intent-form" @submit.prevent>
          <el-form-item class="input-item">
            <el-input
              v-model.trim="userInput"
              :type="chatMessages.length > 0 ? 'text' : 'textarea'"
              :rows="chatMessages.length > 0 ? 1 : 3"
              :placeholder="inputPlaceholder"
              maxlength="500"
              class="intent-input"
              :show-word-limit="chatMessages.length === 0"
              @keyup.enter="chatMessages.length > 0 ? sendChatMessage() : null"
              @keyup.enter.ctrl="chatMessages.length === 0 ? handleStartChat() : null"
            />
          </el-form-item>

          <div class="composer-actions">
            <span class="shortcut-tip">{{ chatMessages.length > 0 ? 'Enter to send' : 'Ctrl+Enter to send' }}</span>
            <el-button
              type="primary"
              class="send-button"
              :icon="Position"
              :loading="chatLoading"
              :disabled="!userInput.trim()"
              aria-label="Send message"
              @click="handleComposerSend"
            >
              {{ chatMessages.length > 0 ? 'Send' : 'Start chat' }}
            </el-button>
          </div>
        </el-form>
      </section>

      <el-alert
        v-if="showSlowWarning"
        type="warning"
        :closable="false"
        class="warning-alert"
        role="alert"
      >
        AI response is taking longer than usual. Please keep this tab open.
      </el-alert>
    </section>
  </AppLayout>
</template>

<script setup>
import { ref, nextTick, computed, onMounted } from "vue"
import { useRouter } from "vue-router"
import { ElMessage, ElMessageBox } from "element-plus"
import {
  Calendar,
  Search,
  ChatDotRound,
  User,
  Position,
  Delete,
  ArrowLeftBold,
  ArrowRightBold
} from "@element-plus/icons-vue"
import { chatWithAgent } from "@/api/agent"
import { clearChatHistory } from "@/api/chat"
import { generateItinerary as generateItineraryApi } from "@/api/trips"
import { useChatSessionsStore } from "@/stores/chatSessions"
import { useUserStore } from "@/stores/user"
import AppLayout from "@/layouts/AppLayout.vue"

const router = useRouter()
const userStore = useUserStore()
const chatSessionsStore = useChatSessionsStore()

const quickPrompts = [
  "I need a relaxing 5-day beach trip under $2000",
  "Plan a food-focused city trip in Japan",
  "I want a romantic weekend in Europe"
]

const userInput = ref("")
const parsedIntent = ref(null)
const loading = ref(false)
const showSlowWarning = ref(false)

const chatMessages = ref([])
const sessionId = ref(null)
const activeSessionId = ref(null)
const conversationSessions = ref([])
const isSidebarCollapsed = ref(false)
const chatLoading = ref(false)
const currentIntent = ref(null)
const currentPhase = ref("chat")
const agentResponse = ref(null)

const normalizeMessages = (messages) => {
  return (messages || []).map((msg) => ({
    role: msg.role,
    content: msg.content,
    timestamp: msg.timestamp || msg.createdAt || new Date().toISOString()
  }))
}

const refreshConversationSessions = async () => {
  if (!userStore.userId) {
    conversationSessions.value = []
    return
  }
  const sessions = await chatSessionsStore.fetchSessions(userStore.userId)
  conversationSessions.value = (sessions || []).map((session) => ({
    id: session.sessionId,
    title: session.title || "New conversation",
    lastMessage: session.lastMessage || "",
    updatedAt: session.updatedAt
  }))
}

const loadConversationById = async (id) => {
  if (!id) return
  const messages = await chatSessionsStore.fetchSessionMessages(userStore.userId, id)
  sessionId.value = id
  activeSessionId.value = id
  chatMessages.value = normalizeMessages(messages)
  parsedIntent.value = null
  currentIntent.value = null
  agentResponse.value = null
  userInput.value = ""
  await nextTick()
  scrollToBottom()
}

const handleSelectConversation = async (id) => {
  if (chatLoading.value || !id) return
  await loadConversationById(id)
}

const createEmptyConversation = async () => {
  if (!userStore.userId) return
  const newSessionId = `session-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`
  await chatSessionsStore.ensureSession(userStore.userId, newSessionId, "New conversation")
  sessionId.value = newSessionId
  activeSessionId.value = sessionId.value
  chatMessages.value = []
  parsedIntent.value = null
  currentIntent.value = null
  agentResponse.value = null
  userInput.value = ""
  await refreshConversationSessions()
}

const handleNewConversation = async () => {
  if (chatLoading.value) return
  await createEmptyConversation()
}

const inputPlaceholder = computed(() => {
  if (chatMessages.value.length > 0) {
    return "Ask follow-up questions or refine preferences..."
  }
  return "Tell me destination, days, budget, and vibe."
})

const nextAction = computed(() => {
  if (
    currentIntent.value &&
    currentIntent.value.needsRecommendation &&
    !currentIntent.value.readyForItinerary &&
    currentPhase.value === "chat"
  ) {
    return {
      kind: "recommend",
      type: "success",
      label: "Get Recommendations",
      hint: "Ready for destination matching based on current conversation."
    }
  }

  if (
    currentIntent.value &&
    currentIntent.value.readyForItinerary &&
    !currentIntent.value.needsRecommendation &&
    currentPhase.value === "chat"
  ) {
    return {
      kind: "itinerary",
      type: "primary",
      label: "Generate Itinerary",
      hint: "Intent is clear. Generate the detailed itinerary now."
    }
  }

  return null
})

const applyQuickPrompt = (prompt) => {
  if (chatLoading.value) return
  userInput.value = prompt
}

const handleStartChat = async () => {
  if (!userInput.value.trim()) {
    ElMessage.warning("Please describe your travel plans")
    return
  }

  if (!userStore.userId) {
    ElMessage.error("Please login first")
    router.push("/login")
    return
  }

  await sendChatMessage()
}

const handleComposerSend = async () => {
  if (chatMessages.value.length === 0) {
    await handleStartChat()
    return
  }
  await sendChatMessage()
}

const sendChatMessage = async () => {
  if (!userInput.value.trim() || chatLoading.value) return

  const userMessage = userInput.value.trim()
  let slowTimer = null

  if (!sessionId.value) {
    await createEmptyConversation()
  }

  chatMessages.value.push({
    role: "user",
    content: userMessage,
    timestamp: new Date()
  })

  userInput.value = ""
  chatLoading.value = true
  showSlowWarning.value = false

  slowTimer = setTimeout(() => {
    if (chatLoading.value) {
      showSlowWarning.value = true
    }
  }, 7000)

  try {
    const response = await chatWithAgent({
      userId: userStore.userId,
      sessionId: sessionId.value,
      message: userMessage
    })

    agentResponse.value = response
    handleAgentResponse(response)
    await refreshConversationSessions()

    await nextTick()
    scrollToBottom()
  } catch (error) {
    console.error("Agent chat failed:", error)
    ElMessage.error(error.response?.data?.message || "Failed to send message")
    chatMessages.value.pop()
  } finally {
    chatLoading.value = false
    showSlowWarning.value = false
    if (slowTimer) clearTimeout(slowTimer)
  }
}

const handleAgentResponse = (response) => {
  if (response.intent) {
    currentIntent.value = response.intent
  }

  switch (response.actionType) {
    case "chat":
      chatMessages.value.push({
        role: "assistant",
        content: response.message,
        timestamp: new Date()
      })
      break

    case "recommend":
      ElMessage.success("Agent is finding destinations for you.")
      router.push({
        name: "Destinations",
        query: {
          sessionId: sessionId.value,
          destination: currentIntent.value?.destination || "",
          interests: currentIntent.value?.interests?.join(",") || "",
          mood: currentIntent.value?.mood || "",
          budget: currentIntent.value?.budget || "",
          days: currentIntent.value?.days || ""
        }
      })
      break

    case "generate":
      if (response.tripId) {
        ElMessage.success("Itinerary generation started.")
        router.push({
          name: "ItineraryProgress",
          params: { tripId: response.tripId }
        })
      }
      break

    case "complete":
      chatMessages.value.push({
        role: "assistant",
        content: response.message || "Task completed.",
        timestamp: new Date()
      })
      currentPhase.value = "chat"
      break

    default:
      chatMessages.value.push({
        role: "assistant",
        content: response.message || "Processing...",
        timestamp: new Date()
      })
  }

}

const handleGetRecommendations = () => {
  if (!currentIntent.value) {
    ElMessage.warning("Please chat first to help me understand your preferences")
    return
  }

  if (!sessionId.value) {
    ElMessage.error("Session not initialized. Please start a conversation first.")
    return
  }

  router.push({
    name: "Destinations",
    query: {
      sessionId: sessionId.value,
      destination: currentIntent.value.destination || "",
      interests: currentIntent.value.interests?.join(",") || "beach",
      mood: currentIntent.value.mood || "relaxing",
      budget: currentIntent.value.budget || "10000",
      days: String(currentIntent.value.days || 5)
    }
  })
}

const handleGenerateItinerary = async () => {
  if (!currentIntent.value) {
    ElMessage.warning("Please specify your travel plans first")
    return
  }

  if (!currentIntent.value.destination) {
    ElMessage.warning("Please specify a destination first")
    return
  }

  loading.value = true

  try {
    const tripId = await generateItineraryApi({
      userId: userStore.userId,
      destinationName: currentIntent.value.destination,
      destinationCountry: currentIntent.value.country || "Unknown",
      durationDays: currentIntent.value.days || 5,
      totalBudget: parseFloat(currentIntent.value.budget) || 10000,
      partySize: 1,
      sessionId: sessionId.value
    })

    ElMessage.success("Itinerary generation started.")
    router.push({
      name: "ItineraryProgress",
      params: { tripId }
    })
  } catch (error) {
    console.error("Failed to generate itinerary:", error)
    ElMessage.error("Failed to generate itinerary")
  } finally {
    loading.value = false
  }
}

const handleNextAction = () => {
  if (!nextAction.value) return
  if (nextAction.value.kind === "recommend") {
    handleGetRecommendations()
    return
  }
  handleGenerateItinerary()
}

const handleClearChat = async () => {
  try {
    await ElMessageBox.confirm(
      "This will clear all chat context and restart planning. Continue?",
      "Start Over",
      {
        confirmButtonText: "Clear",
        cancelButtonText: "Cancel",
        type: "warning",
        customClass: "travel-clear-dialog",
        closeOnClickModal: false,
        autofocus: false
      }
    )

    if (sessionId.value) {
      await chatSessionsStore.removeSession(userStore.userId, sessionId.value)
      await clearChatHistory(sessionId.value, userStore.userId)
    }

    chatMessages.value = []
    sessionId.value = null
    activeSessionId.value = null
    userInput.value = ""
    parsedIntent.value = null
    currentIntent.value = null
    agentResponse.value = null
    showSlowWarning.value = false
    await refreshConversationSessions()
    await createEmptyConversation()

    ElMessage.success("Conversation cleared")
  } catch (error) {
    // User canceled
  }
}

const scrollToBottom = () => {
  const chatHistory = document.querySelector(".chat-history .el-scrollbar__wrap")
  if (chatHistory) {
    setTimeout(() => {
      chatHistory.scrollTo({
        top: chatHistory.scrollHeight,
        behavior: "smooth"
      })
    }, 100)
  }
}

const formatTime = (timestamp) => {
  const date = new Date(timestamp)
  return date.toLocaleTimeString("en-US", { hour: "2-digit", minute: "2-digit" })
}

const getBudgetLabel = (level) => {
  return { 1: "Budget ($)", 2: "Moderate ($$)", 3: "Luxury ($$$)" }[level] || "Unknown"
}

const formatSessionTime = (time) => {
  if (!time) return ""
  const date = new Date(time)
  return date.toLocaleDateString("en-US", { month: "short", day: "numeric" })
}

onMounted(async () => {
  if (!userStore.userId) {
    return
  }
  await refreshConversationSessions()

  if (conversationSessions.value.length > 0) {
    await loadConversationById(conversationSessions.value[0].id)
    return
  }

  await createEmptyConversation()
})
</script>

<style scoped>
.intent-page {
  width: 100%;
  max-width: 100%;
  margin: 0;
  padding-bottom: 0;
  position: relative;
  font-family: "Manrope", "Avenir Next", "Segoe UI", sans-serif;
}

.intent-workspace {
  position: relative;
  width: 100%;
}

.session-sidebar {
  position: fixed;
  top: 140px;
  left: max(16px, calc(50% - 760px));
  width: 248px;
  border: 1px solid #d6e4f2;
  background: rgba(255, 255, 255, 0.7);
  border-radius: 14px;
  padding: 10px;
  height: clamp(360px, calc(100dvh - 320px), 680px);
  z-index: 30;
  transition: width 180ms ease;
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.sidebar-actions {
  margin-bottom: 8px;
}

.sidebar-title {
  margin: 0;
  font-size: 12px;
  letter-spacing: 0.4px;
  text-transform: uppercase;
  color: #4c637d;
  font-weight: 700;
}

.session-list {
  height: calc(100% - 34px);
}

.collapse-btn {
  color: #4e6882;
}

.session-sidebar.is-collapsed {
  width: 56px;
  padding: 8px;
  overflow: hidden;
}

.session-sidebar.is-collapsed .sidebar-header {
  justify-content: center;
}

.session-item {
  width: 100%;
  border: 1px solid #d7e3f0;
  background: #ffffff;
  border-radius: 10px;
  padding: 8px;
  margin-bottom: 8px;
  text-align: left;
  cursor: pointer;
  transition: border-color 150ms ease, box-shadow 150ms ease;
}

.session-item:hover {
  border-color: #99bbdf;
}

.session-item.is-active {
  border-color: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.14);
}

.session-item-title {
  margin: 0;
  font-size: 13px;
  color: #1f405c;
  font-weight: 700;
}

.session-item-preview {
  margin: 4px 0 0;
  color: #5d7690;
  font-size: 12px;
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.session-item-time {
  margin: 6px 0 0;
  font-size: 11px;
  color: #7c93a8;
}

.session-empty {
  color: #6f8599;
  font-size: 12px;
  text-align: center;
  padding: 20px 8px;
}

.chat-center {
  width: min(900px, calc(100vw - 72px));
  margin: 0 auto;
}

.intent-card {
  border-radius: 0;
  border: none;
  background: transparent;
  box-shadow: none;
}

.intent-card :deep(.el-card__body) {
  padding: 0;
}

.intent-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.toolbar-left {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #2563eb;
  box-shadow: 0 0 0 4px rgba(37, 99, 235, 0.15);
}

.toolbar-label {
  font-size: 12px;
  letter-spacing: 0.4px;
  text-transform: uppercase;
  color: #4b627c;
  font-weight: 700;
}

.toolbar-button {
  border-radius: 10px;
  font-weight: 700;
}

.chat-history {
  height: clamp(380px, calc(100dvh - 340px), 680px);
  border-radius: 0;
  border: none;
  background: transparent;
}

.chat-history :deep(.el-scrollbar__wrap) {
  padding: 8px 0;
}

.empty-state {
  min-height: 320px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 14px;
  padding: 40px 16px;
}

.empty-kicker {
  margin: 0;
  color: #5b728b;
  font-size: 14px;
}

.quick-prompts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}

.quick-prompt-button {
  --el-fill-color-light: #ffffff;
  --el-fill-color-blank: #ffffff;
  border: 1px solid #d2e2f1;
  color: #2f4f6a;
  transition: all 180ms ease;
  cursor: pointer;
}

.quick-prompt-button:hover {
  transform: translateY(-1px);
  border-color: #9abde0;
  color: #163754;
}

.chat-message {
  display: flex;
  gap: 12px;
  margin-bottom: 14px;
}

.chat-message.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.chat-message.user .message-avatar {
  color: #ffffff;
  background: linear-gradient(145deg, #3b82f6, #2563eb);
}

.chat-message.assistant .message-avatar {
  color: #2e6188;
  background: #e6f2ff;
}

.message-bubble {
  max-width: min(76%, 720px);
  padding: 12px 14px;
  border-radius: 14px;
  background: #ffffff;
  border: 1px solid #d4e2f1;
  box-shadow: 0 4px 12px rgba(13, 38, 63, 0.06);
}

.chat-message.user .message-bubble {
  background: linear-gradient(145deg, #3b82f6, #2563eb);
  color: #ffffff;
  border: none;
  border-bottom-right-radius: 4px;
}

.chat-message.assistant .message-bubble {
  border-bottom-left-radius: 4px;
}

.message-content {
  font-size: 14px;
  line-height: 1.66;
  white-space: pre-wrap;
  word-break: break-word;
}

.message-time {
  margin-top: 6px;
  font-size: 11px;
  opacity: 0.74;
}

.chat-message.user .message-time {
  text-align: right;
}

.message-bubble.typing {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 68px;
  padding: 13px 15px;
}

.typing-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #8aa2b8;
  animation: typing 1.4s infinite;
}

.typing-dot:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-dot:nth-child(3) {
  animation-delay: 0.4s;
}

.page-composer {
  position: fixed;
  left: 50%;
  bottom: calc(52px + env(safe-area-inset-bottom, 0px));
  transform: translateX(-50%);
  width: min(900px, calc(100vw - 72px));
  z-index: 80;
  border-radius: 18px;
  border: 1px solid #cfdeee;
  background: rgba(252, 254, 255, 0.95);
  box-shadow: 0 18px 32px rgba(12, 34, 56, 0.16);
  backdrop-filter: blur(10px);
  padding: 10px;
}

.assistant-rail {
  margin-bottom: 8px;
  display: grid;
  gap: 8px;
}

.intent-snapshot {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.snapshot-chip {
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid #cfe0f1;
  background: #f5f9ff;
  color: #355673;
  font-size: 12px;
  font-weight: 600;
}

.next-action-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  border: 1px solid #d7e4f1;
  background: #f5f9ff;
  border-radius: 12px;
  padding: 8px;
}

.input-tip {
  margin: 0;
  color: #4f6983;
  font-size: 12px;
}

.next-action-button {
  border-radius: 10px;
  font-weight: 700;
  min-width: 210px;
  height: 38px;
}

.input-item {
  margin-bottom: 8px;
}

.intent-input :deep(.el-textarea__inner),
.intent-input :deep(.el-input__wrapper) {
  border-radius: 14px;
  border: 1px solid #cbdcf0;
  background: #ffffff;
  box-shadow: 0 0 0 1px transparent;
  transition: all 180ms ease;
}

.intent-input :deep(.el-input__wrapper) {
  min-height: 46px;
}

.intent-input :deep(.el-textarea__inner) {
  padding: 10px 12px;
  line-height: 1.6;
}

.intent-input :deep(.el-input__wrapper.is-focus),
.intent-input :deep(.el-textarea__inner:focus) {
  border-color: #7ea9d7;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.16);
}

.composer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.shortcut-tip {
  font-size: 11px;
  color: #70869c;
}

.send-button {
  height: 40px;
  min-width: 120px;
  border-radius: 10px;
  font-weight: 700;
  background: linear-gradient(145deg, #3b82f6, #2563eb);
  border: none;
}

.warning-alert {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  bottom: calc(214px + env(safe-area-inset-bottom, 0px));
  width: min(900px, calc(100vw - 72px));
  z-index: 81;
  border-radius: 12px;
}

@keyframes typing {
  0%,
  60%,
  100% {
    transform: translateY(0);
  }
  30% {
    transform: translateY(-8px);
  }
}

:deep(.travel-clear-dialog) {
  border-radius: 18px;
  border: 1px solid #d5e4ef;
  padding: 6px 4px;
}

:deep(.travel-clear-dialog .el-message-box__title) {
  font-size: 18px;
  color: #14384f;
}

:deep(.travel-clear-dialog .el-message-box__message p) {
  color: #4f6579;
  line-height: 1.6;
}

:deep(.travel-clear-dialog .el-message-box__btns) {
  padding-top: 16px;
}

:deep(.travel-clear-dialog .el-button) {
  border-radius: 10px;
  min-width: 88px;
}

@media (max-width: 1200px) {
  .session-sidebar {
    position: static;
    width: 100%;
    height: auto;
    max-height: 220px;
    margin-bottom: 12px;
  }

  .session-sidebar.is-collapsed {
    width: 100%;
  }

  .chat-center {
    width: min(900px, calc(100vw - 40px));
  }
}

@media (max-width: 768px) {
  .session-sidebar {
    max-height: 200px;
  }

  .chat-history {
    height: calc(100dvh - 330px);
    min-height: 320px;
  }

  .message-bubble {
    max-width: 86%;
  }

  .page-composer {
    width: calc(100vw - 20px);
    bottom: calc(28px + env(safe-area-inset-bottom, 0px));
    border-radius: 14px;
  }

  .next-action-row {
    flex-direction: column;
    align-items: stretch;
  }

  .next-action-button {
    width: 100%;
    min-width: 0;
  }

  .warning-alert {
    width: calc(100vw - 20px);
    bottom: calc(238px + env(safe-area-inset-bottom, 0px));
  }
}

@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation: none !important;
    transition: none !important;
    scroll-behavior: auto !important;
  }
}
</style>
