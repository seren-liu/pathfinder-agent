package com.travel.agent.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.travel.agent.dto.AIDestinationRecommendation;
import com.travel.agent.dto.response.DestinationResponse;
import com.travel.agent.dto.response.ParseIntentResponse;
import com.travel.agent.entity.Destinations;
import com.travel.agent.entity.UserPreferences;
import com.travel.agent.mapper.DestinationsMapper;
import com.travel.agent.service.AIService;
import com.travel.agent.service.AIRecommendationCacheService;
import com.travel.agent.service.DestinationFeaturesService;
import com.travel.agent.service.DestinationsService;
import com.travel.agent.service.GeoapifyService;
import com.travel.agent.service.UserPreferencesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * <p>
 * Travel destinations 服务实现类
 * </p>
 *
 * @author Seren
 * @since 2025-10-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DestinationsServiceImpl extends ServiceImpl<DestinationsMapper, Destinations> 
        implements DestinationsService {

    private final DestinationFeaturesService destinationFeaturesService;
    private final UserPreferencesService userPreferencesService;
    private final AIService aiService;
    private final com.travel.agent.service.RecommendationService recommendationService;
    private final AIRecommendationCacheService cacheService;
    private final GeoapifyService geoapifyService;
    private final ConcurrentMap<String, CompletableFuture<List<AIDestinationRecommendation>>> inFlightRecommendationRequests = new ConcurrentHashMap<>();

    @Override
    public List<DestinationResponse> recommendDestinations(
            Long userId,
            ParseIntentResponse parsedIntent,
            List<Long> excludeIds,
            List<String> excludeNames,
            Boolean forceRefresh
    ) {
        log.info("🎯 Recommending destinations for user: {}, forceRefresh: {}", userId, forceRefresh);

        // 1. 获取用户偏好
        UserPreferences preferences = userPreferencesService.findByUserId(userId);
        if (preferences == null) {
            throw new RuntimeException("User preferences not found");
        }

        List<AIDestinationRecommendation> aiRecommendations = null;

        // 2. 如果不是强制刷新，尝试从缓存获取推荐
        if (forceRefresh == null || !forceRefresh) {
            aiRecommendations = cacheService.getCachedRecommendations(
                userId, 
                parsedIntent.getSessionId(), 
                parsedIntent
            );
        } else {
            log.info("🔄 Force refresh requested, skipping cache");
        }

        // 3. 如果缓存未命中或强制刷新，调用 LangGraph 生成
        if (aiRecommendations == null || aiRecommendations.isEmpty()) {
            log.info("🤖 Generating new recommendations with LangGraph");
            
            // 使用前端传递的 excludeNames
            List<String> excludeNamesList = excludeNames == null
                    ? new ArrayList<>()
                    : excludeNames.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(name -> !name.isEmpty())
                    .distinct()
                    .collect(Collectors.toCollection(ArrayList::new));

            // 使用 single-flight 防止同一意图被重复并发计算
            if (Boolean.TRUE.equals(forceRefresh)) {
                aiRecommendations = recommendationService.generateRecommendations(
                        parsedIntent,
                        userId,
                        excludeNamesList
                );
            } else {
                aiRecommendations = generateRecommendationsWithSingleFlight(
                        userId,
                        parsedIntent,
                        excludeNamesList
                );
            }
            
            // 保存到缓存
            cacheService.cacheRecommendations(
                userId,
                parsedIntent.getSessionId(),
                parsedIntent,
                aiRecommendations
            );
        } else {
            log.info("⚡ Cache hit, using cached recommendations");
        }

        log.info("✅ Returning {} destination recommendations", aiRecommendations.size());

        // 4. 并行获取图片（提高性能）
        List<java.util.concurrent.CompletableFuture<String>> photoFutures = aiRecommendations.stream()
                .map(aiRec -> java.util.concurrent.CompletableFuture.supplyAsync(() ->
                        geoapifyService.getDestinationPhoto(
                                aiRec.getDestinationName(),
                                aiRec.getLatitude(),
                                aiRec.getLongitude()
                        )
                ))
                .toList();

        // 等待所有图片获取完成
        java.util.concurrent.CompletableFuture.allOf(photoFutures.toArray(new java.util.concurrent.CompletableFuture[0])).join();

        // 5. 构建响应
        List<DestinationResponse> responses = new ArrayList<>();
        
        for (int i = 0; i < aiRecommendations.size(); i++) {
            AIDestinationRecommendation aiRec = aiRecommendations.get(i);
            String photoUrl = photoFutures.get(i).join();
            
            log.info("🔍 Mapping destination: {}, recommendedDays: {}", 
                aiRec.getDestinationName(), aiRec.getRecommendedDays());

            // 构建响应（直接使用 AI 生成的数据）
            DestinationResponse response = DestinationResponse.builder()
                    .destinationId(aiRec.getDestinationId())
                    .name(aiRec.getDestinationName())
                    .state(aiRec.getState())
                    .country(aiRec.getCountry())
                    .latitude(aiRec.getLatitude() != null ? java.math.BigDecimal.valueOf(aiRec.getLatitude()) : null)
                    .longitude(aiRec.getLongitude() != null ? java.math.BigDecimal.valueOf(aiRec.getLongitude()) : null)
                    .description(aiRec.getDescription())
                    .budgetLevel(aiRec.getBudgetLevel())
                    .recommendedDays(aiRec.getRecommendedDays())
                    .estimatedCost(aiRec.getEstimatedCost())
                    .bestSeason(aiRec.getBestSeason())
                    .features(aiRec.getFeatures())
                    .matchScore(aiRec.getMatchScore())
                    .recommendReason(aiRec.getRecommendReason())
                    .imageUrl(photoUrl)
                    .build();
            
            responses.add(response);
        }

        log.info("Returning {} destinations", responses.size());
        return responses;
    }

    private List<AIDestinationRecommendation> generateRecommendationsWithSingleFlight(
            Long userId,
            ParseIntentResponse parsedIntent,
            List<String> excludeNames
    ) {
        String key = buildSingleFlightKey(userId, parsedIntent, excludeNames);
        CompletableFuture<List<AIDestinationRecommendation>> future = inFlightRecommendationRequests.computeIfAbsent(
                key,
                ignored -> CompletableFuture
                        .supplyAsync(() -> recommendationService.generateRecommendations(parsedIntent, userId, excludeNames))
                        .whenComplete((result, throwable) -> inFlightRecommendationRequests.remove(key))
        );

        try {
            List<AIDestinationRecommendation> recommendations = future.join();
            return recommendations != null ? recommendations : new ArrayList<>();
        } catch (CompletionException e) {
            throw new RuntimeException("Failed to generate recommendations", e.getCause() != null ? e.getCause() : e);
        }
    }

    private String buildSingleFlightKey(Long userId, ParseIntentResponse parsedIntent, List<String> excludeNames) {
        String sessionId = parsedIntent != null && parsedIntent.getSessionId() != null ? parsedIntent.getSessionId() : "default";
        String intentHash = cacheService.generateIntentHash(parsedIntent);
        String excludePart = excludeNames == null || excludeNames.isEmpty()
                ? ""
                : excludeNames.stream().sorted().collect(Collectors.joining(","));
        return String.format("%d:%s:%s:%s", userId, sessionId, intentHash, excludePart);
    }

    @Override
    public Integer calculateMatchScore(
            List<String> destinationFeatures,
            List<String> preferredFeatures
    ) {
        if (preferredFeatures == null || preferredFeatures.isEmpty()) {
            return 50;
        }

        // 计算交集数量
        long matchCount = destinationFeatures.stream()
                .filter(preferredFeatures::contains)
                .count();

        // 计算匹配百分比
        double matchRatio = (double) matchCount / preferredFeatures.size();
        
        // 转换为 0-100 分数，最低 40 分
        return Math.max(40, (int) (matchRatio * 100));
    }

    /**
     * 根据预算等级和天数估算费用
     */
    private Integer estimateCost(Integer budgetLevel, Integer days) {
        if (days == null) days = 5; // 默认 5 天
        
        int dailyCost = switch (budgetLevel) {
            case 1 -> 150;  // Budget: $150/day
            case 2 -> 300;  // Moderate: $300/day
            case 3 -> 500;  // Luxury: $500/day
            default -> 300;
        };
        
        return dailyCost * days;
    }
}
