package com.travel.agent.ai.agent.unified.tools;

import com.travel.agent.ai.agent.ActionResult;
import com.travel.agent.ai.agent.unified.UnifiedAgentState;
import com.travel.agent.ai.agent.unified.UnifiedAgentTool;
import com.travel.agent.dto.AIDestinationRecommendation;
import com.travel.agent.dto.response.DestinationResponse;
import com.travel.agent.dto.response.ParseIntentResponse;
import com.travel.agent.dto.unified.UnifiedTravelIntent;
import com.travel.agent.service.DestinationsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 统一推荐工具 - 真正执行推荐逻辑
 * 
 * 改进点：
 * 1. 使用 UnifiedAgentState 和 UnifiedTravelIntent
 * 2. 真正执行推荐逻辑，不只是返回消息
 * 3. 更新 Agent 状态中的推荐结果
 * 4. 支持"换一批"功能（excludedDestinations）
 * 5. 详细的日志和错误处理
 */
@Slf4j
@Component("unifiedRecommendationTool")
@RequiredArgsConstructor
public class UnifiedRecommendationTool implements UnifiedAgentTool {
    
    private final DestinationsService destinationsService;
    
    @Override
    public ActionResult execute(UnifiedAgentState state) {
        try {
            log.info("🎯 UnifiedRecommendationTool executing for user: {}, session: {}", 
                state.getUserId(), state.getSessionId());
            
            // 1. 验证状态
            if (state.getIntent() == null) {
                return ActionResult.builder()
                    .toolName(getToolName())
                    .success(false)
                    .observation("Intent is required for recommendations")
                    .error("Missing intent")
                    .build();
            }
            
            UnifiedTravelIntent intent = state.getIntent();
            
            // 2. 检查是否真的需要推荐
            if (!Boolean.TRUE.equals(intent.getNeedsRecommendation())) {
                log.warn("Intent does not need recommendation, but tool was called");
                return ActionResult.builder()
                    .toolName(getToolName())
                    .success(false)
                    .observation("Intent does not require recommendations")
                    .error("needsRecommendation is false")
                    .build();
            }
            
            // 3. 构建 ParseIntentResponse（适配现有服务）
            ParseIntentResponse parsedIntent = buildParsedIntent(intent);
            
            // 4. 获取排除列表
            List<String> excludeNames = state.getExcludedDestinations() != null 
                ? new ArrayList<>(state.getExcludedDestinations()) 
                : new ArrayList<>();
            
            log.info("Calling recommendation service with destination: {}, excludeNames: {}", 
                intent.getDestination(), excludeNames);
            
            // 5. 调用推荐服务
            List<DestinationResponse> recommendations = destinationsService.recommendDestinations(
                state.getUserId(),
                parsedIntent,
                null,  // excludeIds
                excludeNames,
                false  // forceRefresh (首次调用不强制刷新)
            );
            
            if (recommendations == null || recommendations.isEmpty()) {
                log.warn("No recommendations returned from service");
                return ActionResult.builder()
                    .toolName(getToolName())
                    .success(false)
                    .observation("No destinations found matching your preferences. Please try different criteria.")
                    .error("Empty recommendations")
                    .build();
            }
            
            // 6. 转换为 AIDestinationRecommendation（如果需要）
            List<AIDestinationRecommendation> aiRecommendations = convertToAIRecommendations(recommendations);
            
            // 7. 更新状态
            state.setRecommendations(aiRecommendations);
            state.setCurrentPhase(UnifiedAgentState.ExecutionPhase.AWAITING_SELECTION);
            
            // 8. 添加到排除列表（用于下次"换一批"）
            List<String> newExcluded = recommendations.stream()
                .map(DestinationResponse::getName)
                .collect(Collectors.toList());
            state.getExcludedDestinations().addAll(newExcluded);
            
            log.info("✅ Successfully generated {} recommendations", recommendations.size());
            
            String observation = String.format(
                "Found %d destination recommendations: %s. User should select one to proceed.",
                recommendations.size(),
                recommendations.stream()
                    .map(DestinationResponse::getName)
                    .collect(Collectors.joining(", "))
            );
            
            return ActionResult.builder()
                .toolName(getToolName())
                .success(true)
                .observation(observation)
                .result(aiRecommendations)
                .build();
                
        } catch (Exception e) {
            log.error("UnifiedRecommendationTool execution failed", e);
            state.addError("Recommendation failed: " + e.getMessage());
            
            return ActionResult.builder()
                .toolName(getToolName())
                .success(false)
                .observation("Failed to generate recommendations: " + e.getMessage())
                .error(e.getMessage())
                .build();
        }
    }
    
    /**
     * 从 UnifiedTravelIntent 构建 ParseIntentResponse
     * 
     * 这是适配层，用于兼容现有的 DestinationsService
     */
    private ParseIntentResponse buildParsedIntent(UnifiedTravelIntent intent) {
        ParseIntentResponse parsedIntent = new ParseIntentResponse();
        
        // 会话信息
        parsedIntent.setSessionId(intent.getSessionId());
        
        // 目的地信息
        parsedIntent.setDestination(intent.getDestination());
        
        // 偏好信息
        parsedIntent.setMood(intent.getMood());
        parsedIntent.setKeywords(intent.getInterests() != null ? intent.getInterests() : new ArrayList<>());
        parsedIntent.setPreferredFeatures(intent.getInterests() != null ? intent.getInterests() : new ArrayList<>());
        
        // 预算和天数
        parsedIntent.setBudgetLevel(intent.getBudgetLevel());
        parsedIntent.setEstimatedDuration(intent.getDays());
        
        return parsedIntent;
    }
    
    /**
     * 转换 DestinationResponse 为 AIDestinationRecommendation
     */
    private List<AIDestinationRecommendation> convertToAIRecommendations(List<DestinationResponse> responses) {
        List<AIDestinationRecommendation> result = new ArrayList<>();
        
        for (DestinationResponse resp : responses) {
            AIDestinationRecommendation.AIDestinationRecommendationBuilder builder = AIDestinationRecommendation.builder()
                .destinationId(resp.getDestinationId())
                .destinationName(resp.getName())
                .country(resp.getCountry())
                .state(resp.getState())
                .description(resp.getDescription())
                .features(resp.getFeatures())
                .bestSeason(resp.getBestSeason())
                .budgetLevel(resp.getBudgetLevel())
                .recommendedDays(resp.getRecommendedDays())
                .estimatedCost(resp.getEstimatedCost())
                .matchScore(resp.getMatchScore())
                .recommendReason(resp.getRecommendReason());
            
            // 转换 BigDecimal 为 Double
            if (resp.getLatitude() != null) {
                builder.latitude(resp.getLatitude().doubleValue());
            }
            if (resp.getLongitude() != null) {
                builder.longitude(resp.getLongitude().doubleValue());
            }
            
            result.add(builder.build());
        }
        
        return result;
    }
    
    @Override
    public String getToolName() {
        return "recommend_destinations";
    }
    
    @Override
    public String getDescription() {
        return "Recommend travel destinations based on user preferences. " +
               "This tool will search for suitable destinations matching the user's intent " +
               "(destination preference, interests, mood, budget, duration) and return 3 recommendations. " +
               "The recommendations will be stored in the agent state for user selection.";
    }
}
