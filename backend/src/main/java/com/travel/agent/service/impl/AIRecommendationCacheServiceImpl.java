package com.travel.agent.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travel.agent.dto.AIDestinationRecommendation;
import com.travel.agent.dto.response.ParseIntentResponse;
import com.travel.agent.entity.AIRecommendationCache;
import com.travel.agent.mapper.AIRecommendationCacheMapper;
import com.travel.agent.service.AIRecommendationCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * AI 推荐缓存服务实现
 *
 * @author Seren
 * @since 2025-10-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIRecommendationCacheServiceImpl implements AIRecommendationCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final AIRecommendationCacheMapper cacheMapper;

    /**
     * Redis 缓存过期时间：30 分钟
     */
    private static final Duration CACHE_EXPIRATION = Duration.ofMinutes(30);

    @Override
    public List<AIDestinationRecommendation> getCachedRecommendations(
            Long userId,
            String sessionId,
            ParseIntentResponse intent
    ) {
        String intentHash = generateIntentHash(intent);
        String cacheKey = getCacheKey(userId, sessionId, intentHash);

        // 1. 尝试从 Redis 获取
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.info("✅ Cache hit (Redis): userId={}, sessionId={}, intentHash={}", 
                        userId, sessionId, intentHash);
                @SuppressWarnings("unchecked")
                List<AIDestinationRecommendation> recommendations = (List<AIDestinationRecommendation>) cached;
                return recommendations;
            }
        } catch (Exception e) {
            log.warn("Redis cache read failed: {}", e.getMessage());
        }

        // 2. 尝试从数据库获取（同一 session 内）
        try {
            AIRecommendationCache dbCache = cacheMapper.selectOne(
                    new LambdaQueryWrapper<AIRecommendationCache>()
                            .eq(AIRecommendationCache::getUserId, userId)
                            .eq(AIRecommendationCache::getSessionId, sessionId)
                            .eq(AIRecommendationCache::getIntentHash, intentHash)
                            .orderByDesc(AIRecommendationCache::getCreatedAt)
                            .last("LIMIT 1")
            );

            if (dbCache != null && dbCache.getRecommendations() != null && !dbCache.getRecommendations().isEmpty()) {
                log.info("✅ Cache hit (Database): userId={}, sessionId={}, intentHash={}", 
                        userId, sessionId, intentHash);

                // 重新放入 Redis
                try {
                    redisTemplate.opsForValue().set(cacheKey, dbCache.getRecommendations(), CACHE_EXPIRATION);
                    log.info("📝 Restored to Redis: key={}", cacheKey);
                } catch (Exception e) {
                    log.warn("Failed to restore to Redis: {}", e.getMessage());
                }

                return dbCache.getRecommendations();
            }
        } catch (Exception e) {
            log.error("Database cache read failed: {}", e.getMessage(), e);
        }

        log.info("❌ Cache miss: userId={}, sessionId={}, intentHash={}", userId, sessionId, intentHash);
        return null;
    }

    @Override
    public void cacheRecommendations(
            Long userId,
            String sessionId,
            ParseIntentResponse intent,
            List<AIDestinationRecommendation> recommendations
    ) {
        if (recommendations == null || recommendations.isEmpty()) {
            log.warn("Cannot cache empty recommendations");
            return;
        }

        String intentHash = generateIntentHash(intent);
        String cacheKey = getCacheKey(userId, sessionId, intentHash);

        // 1. 保存到 Redis
        try {
            redisTemplate.opsForValue().set(cacheKey, recommendations, CACHE_EXPIRATION);
            log.info("💾 Saved to Redis: key={}, count={}", cacheKey, recommendations.size());
        } catch (Exception e) {
            log.error("Failed to save to Redis: {}", e.getMessage(), e);
        }

        // 2. 保存到数据库
        try {
            AIRecommendationCache cache = AIRecommendationCache.builder()
                    .userId(userId)
                    .sessionId(sessionId)
                    .intentHash(intentHash)
                    .mood(intent.getMood())
                    .keywords(JSON.toJSONString(intent.getKeywords()))
                    .preferredFeatures(JSON.toJSONString(intent.getPreferredFeatures()))
                    .budgetLevel((byte) (intent.getBudgetLevel() != null ? intent.getBudgetLevel() : 2))
                    .estimatedDuration(intent.getEstimatedDuration())
                    .recommendations(recommendations)
                    .createdAt(LocalDateTime.now())
                    .expiresAt(LocalDateTime.now().plus(CACHE_EXPIRATION))
                    .build();

            cacheMapper.insertJsonb(cache);
            log.info("💾 Saved to Database: userId={}, sessionId={}, id={}", userId, sessionId, cache.getId());
        } catch (Exception e) {
            log.error("Failed to save to Database: {}", e.getMessage(), e);
        }
    }

    @Override
    public String generateIntentHash(ParseIntentResponse intent) {
        // 将意图关键信息拼接成字符串
        String intentStr = String.format("%s|%s|%s|%d|%d",
                intent.getMood() != null ? intent.getMood() : "",
                intent.getKeywords() != null ? String.join(",", intent.getKeywords()) : "",
                intent.getPreferredFeatures() != null ? String.join(",", intent.getPreferredFeatures()) : "",
                intent.getBudgetLevel() != null ? intent.getBudgetLevel() : 0,
                intent.getEstimatedDuration() != null ? intent.getEstimatedDuration() : 0
        );

        // 使用 MD5 生成哈希
        return DigestUtils.md5DigestAsHex(intentStr.getBytes());
    }

    @Override
    public void clearUserCache(Long userId) {
        // 清除 Redis 中该用户的所有缓存
        try {
            String pattern = String.format("ai:recommendations:%d:*", userId);
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("🗑️ Cleared Redis cache for user: userId={}, count={}", userId, keys.size());
            }
        } catch (Exception e) {
            log.error("Failed to clear Redis cache: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取 Redis 缓存键
     */
    private String getCacheKey(Long userId, String sessionId, String intentHash) {
        return String.format("ai:recommendations:%d:%s:%s", userId, sessionId, intentHash);
    }
}
