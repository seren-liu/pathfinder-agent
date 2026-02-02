import request from './request'

/**
 * 统一的 Agent API
 * 
 * Agent 会自主决策：
 * - 何时进行对话收集信息
 * - 何时推荐目的地
 * - 何时生成行程
 * - 何时结束任务
 * 
 * @param {Object} data - { userId, sessionId, message }
 * @returns {Promise<AgentResponse>}
 * 
 * AgentResponse:
 * - actionType: "chat" | "recommend" | "generate" | "complete"
 * - message: AI response text
 * - intent: TravelIntent object
 * - recommendations: List of destination recommendations
 * - tripId: Generated trip ID
 * - reasoningHistory: ReAct steps
 * - metadata: Additional data
 */
export function chatWithAgent(data) {
  return request({
    url: '/agent/chat',
    method: 'post',
    params: {
      userId: data.userId,
      sessionId: data.sessionId
    },
    headers: {
      'Content-Type': 'text/plain'
    },
    data: data.message,
    transformRequest: [(data) => data]
  })
}
