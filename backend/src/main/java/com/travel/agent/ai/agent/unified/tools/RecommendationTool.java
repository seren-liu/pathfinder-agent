package com.travel.agent.ai.agent.unified.tools;

import com.travel.agent.ai.agent.ActionResult;
import com.travel.agent.ai.agent.unified.AgentState;
import com.travel.agent.ai.agent.unified.UnifiedAgentTool;
import com.travel.agent.dto.AIDestinationRecommendation;
import com.travel.agent.dto.TravelIntent;
import com.travel.agent.dto.response.DestinationResponse;
import com.travel.agent.dto.response.ParseIntentResponse;
import com.travel.agent.service.DestinationsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 推荐工具 - 包装现有 DestinationsService
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationTool implements UnifiedAgentTool {
    
    private final DestinationsService destinationsService;
    
    @Override
    public ActionResult execute(AgentState state) {
        try {
            log.info("🎯 RecommendationTool executing for user: {}", state.getUserId());
            
            // 从 TravelIntent 构建 ParseIntentResponse
            ParseIntentResponse parsedIntent = buildParsedIntent(state);
            
            // 调用现有服务
            List<DestinationResponse> recommendations = destinationsService.recommendDestinations(
                state.getUserId(),
                parsedIntent,
                null,  // excludeIds
                null,  // excludeNames
                false  // forceRefresh
            );

            List<AIDestinationRecommendation> aiRecommendations = recommendations.stream()
                    .map(this::toAIDestinationRecommendation)
                    .toList();
            
            return ActionResult.builder()
                .toolName("recommend_destinations")
                .success(true)
                .observation(String.format("Found %d destination recommendations", aiRecommendations.size()))
                .result(aiRecommendations)
                .build();
                
        } catch (Exception e) {
            log.error("RecommendationTool execution failed", e);
            return ActionResult.builder()
                .toolName("recommend_destinations")
                .success(false)
                .observation("Failed to generate recommendations: " + e.getMessage())
                .error(e.getMessage())
                .build();
        }
    }
    
    /**
     * 从 TravelIntent 构建 ParseIntentResponse
     */
    private ParseIntentResponse buildParsedIntent(AgentState state) {
        TravelIntent intent = state.getIntent();
        
        if (intent == null) {
            throw new IllegalStateException("Intent is required for recommendations");
        }
        
        ParseIntentResponse parsedIntent = new ParseIntentResponse();
        parsedIntent.setSessionId(state.getSessionId());
        parsedIntent.setDestination(intent.getDestination());
        
        // 从 TravelIntent 提取信息
        if (intent.getMood() != null) {
            parsedIntent.setMood(intent.getMood());
        }
        
        if (intent.getInterests() != null) {
            parsedIntent.setKeywords(intent.getInterests());
            parsedIntent.setPreferredFeatures(intent.getInterests());
        }
        
        if (intent.getDays() != null) {
            parsedIntent.setEstimatedDuration(intent.getDays());
        }
        
        // 预算等级（简单映射）
        if (intent.getBudget() != null) {
            parsedIntent.setBudgetLevel(parseBudgetLevel(intent.getBudget()));
        } else {
            parsedIntent.setBudgetLevel(2); // 默认中等
        }
        
        return parsedIntent;
    }
    
    /**
     * 解析预算等级
     */
    private Integer parseBudgetLevel(String budget) {
        try {
            int amount = Integer.parseInt(budget.replaceAll("[^0-9]", ""));
            if (amount < 5000) return 1;
            if (amount < 15000) return 2;
            return 3;
        } catch (Exception e) {
            return 2; // 默认中等
        }
    }
    
    @Override
    public String getToolName() {
        return "recommend_destinations";
    }
    
    @Override
    public String getDescription() {
        return "Recommend destinations based on user preferences";
    }

    private AIDestinationRecommendation toAIDestinationRecommendation(DestinationResponse destination) {
        return AIDestinationRecommendation.builder()
                .destinationId(destination.getDestinationId())
                .destinationName(destination.getName())
                .country(destination.getCountry())
                .state(destination.getState())
                .description(destination.getDescription())
                .features(destination.getFeatures())
                .bestSeason(destination.getBestSeason())
                .budgetLevel(destination.getBudgetLevel())
                .recommendedDays(destination.getRecommendedDays())
                .estimatedCost(destination.getEstimatedCost())
                .matchScore(destination.getMatchScore())
                .recommendReason(destination.getRecommendReason())
                .latitude(destination.getLatitude() != null ? destination.getLatitude().doubleValue() : null)
                .longitude(destination.getLongitude() != null ? destination.getLongitude().doubleValue() : null)
                .build();
    }
}
