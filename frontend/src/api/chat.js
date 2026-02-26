import request from './request'

/**
 * 发送聊天消息
 * @param {Object} data - { userId, sessionId, message }
 * @returns {Promise}
 */
export const sendChatMessage = (data) => {
  return request({
    url: '/chat',
    method: 'post',
    data
  })
}

/**
 * 获取会话历史
 * @param {String} sessionId - 会话ID
 * @param {Number} userId - 用户ID
 * @returns {Promise}
 */
export const getChatHistory = (sessionId, userId) => {
  return request({
    url: `/chat/history/${sessionId}`,
    method: 'get',
    params: { userId }
  })
}

/**
 * 清除会话历史
 * @param {String} sessionId - 会话ID
 * @param {Number} userId - 用户ID
 * @returns {Promise}
 */
export const clearChatHistory = (sessionId, userId) => {
  return request({
    url: `/chat/${sessionId}`,
    method: 'delete',
    params: { userId }
  })
}
