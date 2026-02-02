package com.travel.agent.service;

import com.travel.agent.dto.AIDestinationRecommendation;
import com.travel.agent.dto.response.ParseIntentResponse;

import java.util.List;

/**
 * AI 推荐缓存服务接口
 *
 * @author Seren
 * @since 2025-10-18
 */
public interface AIRecommendationCacheService {

    /**
     * 从缓存获取推荐（先 Redis，后数据库）
     *
     * @param userId 用户 ID
     * @param sessionId 会话 ID
     * @param intent 解析的意图
     * @return 缓存的推荐列表，如果未命中返回 null
     */
    List<AIDestinationRecommendation> getCachedRecommendations(
            Long userId,
            String sessionId,
            ParseIntentResponse intent
    );

    /**
     * 保存推荐到缓存（Redis + 数据库）
     *
     * @param userId 用户 ID
     * @param sessionId 会话 ID
     * @param intent 解析的意图
     * @param recommendations 推荐列表
     */
    void cacheRecommendations(
            Long userId,
            String sessionId,
            ParseIntentResponse intent,
            List<AIDestinationRecommendation> recommendations
    );

    /**
     * 生成意图哈希（用于缓存键）
     *
     * @param intent 解析的意图
     * @return MD5 哈希值
     */
    String generateIntentHash(ParseIntentResponse intent);

    /**
     * 清除用户的所有推荐缓存
     *
     * @param userId 用户 ID
     */
    void clearUserCache(Long userId);
}
