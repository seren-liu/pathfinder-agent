package com.travel.agent.service;

import com.travel.agent.dto.TravelIntent;

/**
 * 意图分析服务接口
 */
public interface IntentAnalysisService {
    
    /**
     * 分析用户输入的旅行意图
     * 
     * @param userInput 用户输入
     * @return 意图分析结果
     */
    TravelIntent analyzeIntent(String userInput);
    
    /**
     * 判断是否为首次对话
     * 
     * @param sessionId 会话ID
     * @return 是否为首次对话
     */
    boolean isFirstMessage(String sessionId);
}
