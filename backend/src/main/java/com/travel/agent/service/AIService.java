package com.travel.agent.service;

import com.travel.agent.dto.AIDestinationRecommendation;
import com.travel.agent.dto.response.ParseIntentResponse;
import com.travel.agent.entity.UserPreferences;

import java.util.List;

public interface AIService {
    
    /**
     * 解析用户旅行意图
     */
    ParseIntentResponse parseIntent(String userInput, Long userId);
    
    /**
     * 生成目的地推荐理由
     */
    String generateRecommendReason(
        String destinationName,
        String destinationDescription,
        List<String> destinationFeatures,
        String userMood,
        List<String> userInterests,
        String travelStyle
    );
    
    /**
     * 使用 AI 直接生成目的地推荐（不依赖数据库）
     * 
     * @param parsedIntent 解析的用户意图
     * @param userPreferences 用户偏好
     * @param excludeDestinationNames 需要排除的目的地名称列表（用于换一批）
     * @return AI 生成的目的地推荐列表（3 个）
     */
    List<AIDestinationRecommendation> generateDestinationRecommendations(
        ParseIntentResponse parsedIntent,
        UserPreferences userPreferences,
        List<String> excludeDestinationNames
    );
    
    /**
     * 通用 AI 对话方法（用于行程生成等）
     * 
     * @param prompt 提示词
     * @return AI 响应内容
     */
    String chat(String prompt);
}
