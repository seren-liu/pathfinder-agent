package com.travel.agent.service;

import com.travel.agent.dto.response.ChatResponse;
import com.travel.agent.entity.ConversationHistory;

import java.util.List;

/**
 * 多轮对话服务接口
 */
public interface ConversationService {
    
    /**
     * 多轮对话
     */
    ChatResponse chat(Long userId, String sessionId, String userMessage);
    
    /**
     * 获取对话历史
     */
    List<ConversationHistory> getHistory(Long userId, String sessionId);
    
    /**
     * 清除对话历史
     */
    void clearHistory(Long userId, String sessionId);
}
