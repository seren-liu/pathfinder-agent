package com.travel.agent.service;

import com.travel.agent.dto.unified.UnifiedTravelIntent;

/**
 * 统一意图分析服务接口
 * 
 * 使用 UnifiedTravelIntent 替代 TravelIntent
 */
public interface UnifiedIntentAnalysisService {
    
    /**
     * 分析用户输入的旅行意图
     * 
     * @param userInput 用户输入
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @return 统一意图分析结果
     */
    UnifiedTravelIntent analyzeIntent(String userInput, Long userId, String sessionId);
    
    /**
     * 判断是否为首次对话
     * 
     * @param sessionId 会话ID
     * @return 是否为首次对话
     */
    boolean isFirstMessage(String sessionId);
}
