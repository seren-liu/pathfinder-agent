import request from "@/utils/request"

export const listChatSessions = (userId, limit = 30) => {
  return request({
    url: "/chat/sessions",
    method: "get",
    params: { userId, limit }
  })
}

export const createChatSession = (userId, sessionId, title) => {
  return request({
    url: "/chat/sessions",
    method: "post",
    params: { userId, sessionId, title }
  })
}

export const listChatSessionMessages = (userId, sessionId, limit = 200) => {
  return request({
    url: `/chat/sessions/${sessionId}/messages`,
    method: "get",
    params: { userId, limit }
  })
}

export const deleteChatSession = (userId, sessionId) => {
  return request({
    url: `/chat/sessions/${sessionId}`,
    method: "delete",
    params: { userId }
  })
}

