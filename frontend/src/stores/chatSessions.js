import { defineStore } from "pinia"
import { ref } from "vue"
import {
  listChatSessions,
  createChatSession,
  listChatSessionMessages,
  deleteChatSession
} from "@/api/chatSessions"

export const useChatSessionsStore = defineStore("chatSessions", () => {
  const sessions = ref([])
  const loadingSessions = ref(false)

  const fetchSessions = async (userId, limit = 30) => {
    if (!userId) return []
    loadingSessions.value = true
    try {
      const data = await listChatSessions(userId, limit)
      sessions.value = Array.isArray(data) ? data : []
      return sessions.value
    } finally {
      loadingSessions.value = false
    }
  }

  const ensureSession = async (userId, sessionId, title = "") => {
    if (!userId) throw new Error("User not logged in")
    const created = await createChatSession(userId, sessionId, title)
    await fetchSessions(userId)
    return created
  }

  const fetchSessionMessages = async (userId, sessionId, limit = 200) => {
    if (!userId || !sessionId) return []
    const data = await listChatSessionMessages(userId, sessionId, limit)
    return Array.isArray(data) ? data : []
  }

  const removeSession = async (userId, sessionId) => {
    if (!userId || !sessionId) return
    await deleteChatSession(userId, sessionId)
    await fetchSessions(userId)
  }

  return {
    sessions,
    loadingSessions,
    fetchSessions,
    ensureSession,
    fetchSessionMessages,
    removeSession
  }
})

