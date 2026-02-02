package com.travel.agent.service.impl;

import com.travel.agent.ai.graph.RecommendationGraph;
import com.travel.agent.ai.state.RecommendationState;
import com.travel.agent.dto.AIDestinationRecommendation;
import com.travel.agent.dto.response.ParseIntentResponse;
import com.travel.agent.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 推荐服务实现
 * 
 * 使用 LangGraph 执行推荐流程：
 * 1. 构建初始状态
 * 2. 执行 RecommendationGraph
 * 3. 转换结果为 DTO
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationServiceImpl implements RecommendationService {
    
    private final RecommendationGraph recommendationGraph;
    
    @Override
    public List<AIDestinationRecommendation> generateRecommendations(
        ParseIntentResponse parsedIntent,
        Long userId,
        List<String> excludeNames
    ) {
        log.info("🚀 RecommendationService: Generating recommendations using LangGraph");
        log.info("User: {}, Destination: {}, Interests: {}", 
            userId, 
            parsedIntent.getDestination(), 
            parsedIntent.getKeywords());
        
        try {
            // 1. 构建初始状态
            Map<String, Object> initialData = buildInitialState(parsedIntent, userId, excludeNames);
            
            // 2. 编译并执行图
            CompiledGraph<RecommendationState> graph = recommendationGraph.buildGraph();
            
            log.info("📊 Executing RecommendationGraph...");
            Optional<RecommendationState> result = graph.invoke(initialData);
            
            if (result.isEmpty()) {
                log.error("❌ RecommendationGraph returned empty result");
                return new ArrayList<>();
            }
            
            RecommendationState finalState = result.get();
            
            // 3. 检查执行结果
            if (!finalState.getCompleted()) {
                log.warn("⚠️ RecommendationGraph did not complete successfully");
                List<String> errors = finalState.getErrors();
                if (!errors.isEmpty()) {
                    log.error("Errors: {}", errors);
                }
            }
            
            // 4. 转换结果
            List<AIDestinationRecommendation> recommendations = convertToRecommendations(finalState);
            
            log.info("✅ Generated {} recommendations using LangGraph", recommendations.size());
            
            return recommendations;
            
        } catch (Exception e) {
            log.error("❌ RecommendationService failed", e);
            // 返回空列表而不是抛出异常
            return new ArrayList<>();
        }
    }
    
    /**
     * 构建初始状态
     */
    private Map<String, Object> buildInitialState(
        ParseIntentResponse parsedIntent,
        Long userId,
        List<String> excludeNames
    ) {
        Map<String, Object> data = new HashMap<>();
        
        // 用户信息
        data.put("userId", userId);
        data.put("sessionId", parsedIntent.getSessionId());
        
        // 目的地偏好
        String destination = parsedIntent.getDestination();
        if (destination != null && !destination.isEmpty()) {
            data.put("destinationPreference", destination);
        }
        
        // 兴趣
        List<String> interests = parsedIntent.getKeywords();
        if (interests == null) {
            interests = new ArrayList<>();
        }
        data.put("interests", interests);
        
        // 心情
        String mood = parsedIntent.getMood();
        if (mood != null && !mood.isEmpty()) {
            data.put("mood", mood);
        } else {
            data.put("mood", "relaxing");
        }
        
        // 预算等级
        Integer budgetLevel = parsedIntent.getBudgetLevel();
        if (budgetLevel == null || budgetLevel < 1 || budgetLevel > 3) {
            budgetLevel = 2; // 默认中等
        }
        data.put("budgetLevel", budgetLevel);
        
        // 天数
        Integer days = parsedIntent.getEstimatedDuration();
        if (days == null || days <= 0) {
            days = 5; // 默认值
        }
        data.put("days", days);
        
        // 排除列表
        if (excludeNames == null) {
            excludeNames = new ArrayList<>();
        }
        data.put("excludeNames", excludeNames);
        
        log.debug("Initial state: destination={}, interests={}, mood={}, budgetLevel={}, days={}, exclude={}", 
            destination, interests, mood, budgetLevel, days, excludeNames);
        
        return data;
    }
    
    /**
     * 转换 RecommendationState 为 AIDestinationRecommendation 列表
     */
    private List<AIDestinationRecommendation> convertToRecommendations(RecommendationState state) {
        List<Map<String, Object>> recommendations = state.getRecommendations();
        
        if (recommendations == null || recommendations.isEmpty()) {
            log.warn("No recommendations in final state");
            return new ArrayList<>();
        }
        
        return recommendations.stream()
            .map(this::convertToRecommendation)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    /**
     * 转换单个推荐
     */
    private AIDestinationRecommendation convertToRecommendation(Map<String, Object> rec) {
        try {
            AIDestinationRecommendation recommendation = new AIDestinationRecommendation();
            
            // 基本信息
            recommendation.setDestinationName((String) rec.get("name"));
            recommendation.setCountry((String) rec.get("country"));
            recommendation.setDescription((String) rec.get("description"));
            
            // 推荐理由
            String reason = (String) rec.get("recommendReason");
            if (reason != null && !reason.isEmpty()) {
                recommendation.setRecommendReason(reason);
            } else {
                recommendation.setRecommendReason("A great destination that matches your preferences.");
            }
            
            // 特性
            Object featuresObj = rec.get("features");
            if (featuresObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> features = (List<String>) featuresObj;
                recommendation.setFeatures(features);
            } else if (featuresObj instanceof String) {
                recommendation.setFeatures(Arrays.asList(((String) featuresObj).split(",")));
            } else {
                recommendation.setFeatures(new ArrayList<>());
            }
            
            // 预算等级
            Object budgetLevelObj = rec.get("budgetLevel");
            if (budgetLevelObj instanceof Number) {
                recommendation.setBudgetLevel(((Number) budgetLevelObj).intValue());
            } else {
                recommendation.setBudgetLevel(2);
            }
            
            // 匹配分数
            Object matchScoreObj = rec.get("matchScore");
            if (matchScoreObj instanceof Number) {
                recommendation.setMatchScore(((Number) matchScoreObj).intValue());
            } else {
                recommendation.setMatchScore(85);
            }
            
            // 坐标（可选）
            Object latObj = rec.get("latitude");
            Object lonObj = rec.get("longitude");
            if (latObj instanceof Number && lonObj instanceof Number) {
                recommendation.setLatitude(((Number) latObj).doubleValue());
                recommendation.setLongitude(((Number) lonObj).doubleValue());
            }
            
            return recommendation;
            
        } catch (Exception e) {
            log.error("Failed to convert recommendation: {}", rec, e);
            return null;
        }
    }
}
