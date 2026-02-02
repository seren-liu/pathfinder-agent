package com.travel.agent.service;

import com.travel.agent.dto.AIDestinationRecommendation;
import com.travel.agent.dto.response.ParseIntentResponse;

import java.util.List;

/**
 * 推荐服务接口
 * 
 * 使用 LangGraph 实现智能推荐
 */
public interface RecommendationService {
    
    /**
     * 生成目的地推荐（使用 LangGraph）
     * 
     * @param parsedIntent 解析后的意图
     * @param userId 用户ID
     * @param excludeNames 排除的目的地名称
     * @return 推荐列表
     */
    List<AIDestinationRecommendation> generateRecommendations(
        ParseIntentResponse parsedIntent,
        Long userId,
        List<String> excludeNames
    );
}
