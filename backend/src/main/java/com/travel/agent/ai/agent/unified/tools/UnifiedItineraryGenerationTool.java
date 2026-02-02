package com.travel.agent.ai.agent.unified.tools;

import com.travel.agent.ai.agent.ActionResult;
import com.travel.agent.ai.agent.unified.UnifiedAgentState;
import com.travel.agent.ai.agent.unified.UnifiedAgentTool;
import com.travel.agent.dto.AIDestinationRecommendation;
import com.travel.agent.dto.request.CreateTripRequest;
import com.travel.agent.dto.request.GenerateItineraryRequest;
import com.travel.agent.dto.unified.UnifiedTravelIntent;
import com.travel.agent.service.ItineraryGenerationService;
import com.travel.agent.service.TripsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 统一行程生成工具
 * 
 * 改进点：
 * 1. 使用 UnifiedAgentState 和 UnifiedTravelIntent
 * 2. 完整的数据验证
 * 3. 更新状态中的 tripId 和 itineraryStatus
 * 4. 详细的日志记录
 */
@Slf4j
@Component("unifiedItineraryGenerationTool")
@RequiredArgsConstructor
public class UnifiedItineraryGenerationTool implements UnifiedAgentTool {
    
    private final TripsService tripsService;
    private final ItineraryGenerationService itineraryGenerationService;
    
    @Override
    public ActionResult execute(UnifiedAgentState state) {
        try {
            log.info("✈️ UnifiedItineraryGenerationTool executing for user: {}, session: {}", 
                state.getUserId(), state.getSessionId());
            
            // 1. 验证状态
            UnifiedTravelIntent intent = state.getIntent();
            if (intent == null) {
                return ActionResult.builder()
                    .toolName(getToolName())
                    .success(false)
                    .observation("Intent is required for itinerary generation")
                    .error("Missing intent")
                    .build();
            }
            
            // 2. 检查是否准备好生成行程
            if (!Boolean.TRUE.equals(intent.getReadyForItinerary())) {
                log.warn("Intent is not ready for itinerary generation");
                return ActionResult.builder()
                    .toolName(getToolName())
                    .success(false)
                    .observation("Intent is not ready for itinerary. Need more information or destination selection.")
                    .error("readyForItinerary is false")
                    .build();
            }
            
            // 3. 检查是否有足够信息
            if (!intent.hasEnoughInfoForItinerary()) {
                log.warn("Not enough information for itinerary generation");
                return ActionResult.builder()
                    .toolName(getToolName())
                    .success(false)
                    .observation("Missing required information: destination, days, or budget")
                    .error("Insufficient information")
                    .build();
            }
            
            // 4. 构建 GenerateItineraryRequest
            GenerateItineraryRequest request = buildRequest(state);
            
            // 5. 创建 Trip 记录
            CreateTripRequest createTripRequest = new CreateTripRequest();
            createTripRequest.setUserId(state.getUserId());
            createTripRequest.setDurationDays(request.getDurationDays());
            createTripRequest.setTotalBudget(request.getTotalBudget());
            createTripRequest.setPartySize(request.getPartySize());
            
            Long tripId = tripsService.createTrip(createTripRequest);
            
            log.info("✅ Trip created successfully: tripId={}", tripId);
            
            // 6. 更新状态
            state.setTripId(tripId);
            state.setItineraryStatus(UnifiedAgentState.ItineraryStatus.PLANNING);
            state.setCurrentPhase(UnifiedAgentState.ExecutionPhase.GENERATING_ITINERARY);
            
            // 7. 异步生成行程
            itineraryGenerationService.generateItineraryAsync(tripId, request);
            
            log.info("✅ Itinerary generation started asynchronously");
            
            String observation = String.format(
                "Itinerary generation started successfully for %s, %d days, budget %s. Trip ID: %d. " +
                "The itinerary is being generated in the background.",
                request.getDestinationName(),
                request.getDurationDays(),
                request.getTotalBudget(),
                tripId
            );
            
            return ActionResult.builder()
                .toolName(getToolName())
                .success(true)
                .observation(observation)
                .result(tripId)
                .build();
                
        } catch (Exception e) {
            log.error("UnifiedItineraryGenerationTool execution failed", e);
            state.addError("Itinerary generation failed: " + e.getMessage());
            state.setItineraryStatus(UnifiedAgentState.ItineraryStatus.FAILED);
            
            return ActionResult.builder()
                .toolName(getToolName())
                .success(false)
                .observation("Failed to generate itinerary: " + e.getMessage())
                .error(e.getMessage())
                .build();
        }
    }
    
    /**
     * 从 UnifiedAgentState 构建 GenerateItineraryRequest
     */
    private GenerateItineraryRequest buildRequest(UnifiedAgentState state) {
        UnifiedTravelIntent intent = state.getIntent();
        AIDestinationRecommendation selectedDest = state.getSelectedDestination();
        
        GenerateItineraryRequest request = new GenerateItineraryRequest();
        request.setUserId(state.getUserId());
        request.setSessionId(state.getSessionId());
        
        // 设置目的地信息
        if (selectedDest != null) {
            // 优先使用用户选择的推荐目的地
            request.setDestinationName(selectedDest.getDestinationName());
            request.setDestinationCountry(selectedDest.getCountry());
            
            if (selectedDest.getLatitude() != null) {
                request.setDestinationLatitude(BigDecimal.valueOf(selectedDest.getLatitude()));
            }
            if (selectedDest.getLongitude() != null) {
                request.setDestinationLongitude(BigDecimal.valueOf(selectedDest.getLongitude()));
            }
        } else if (intent.getDestination() != null) {
            // 使用意图中的目的地
            request.setDestinationName(intent.getDestination());
            request.setDestinationCountry(intent.getDestinationCountry() != null ? 
                intent.getDestinationCountry() : "Unknown");
            
            if (intent.getDestinationLatitude() != null) {
                request.setDestinationLatitude(BigDecimal.valueOf(intent.getDestinationLatitude()));
            }
            if (intent.getDestinationLongitude() != null) {
                request.setDestinationLongitude(BigDecimal.valueOf(intent.getDestinationLongitude()));
            }
        } else {
            throw new IllegalStateException("Destination is required for itinerary generation");
        }
        
        // 设置天数
        request.setDurationDays(intent.getDays() != null ? intent.getDays() : 5);
        
        // 设置预算
        BigDecimal budget = intent.getBudget();
        if (budget == null) {
            // 从预算等级估算
            budget = UnifiedTravelIntent.estimateBudget(intent.getBudgetLevel(), intent.getDays());
        }
        request.setTotalBudget(budget);
        
        // 设置人数
        request.setPartySize(intent.getPartySize() != null ? intent.getPartySize() : 2);
        
        // 设置偏好
        if (intent.getInterests() != null && !intent.getInterests().isEmpty()) {
            request.setPreferences(String.join(", ", intent.getInterests()));
        }
        
        log.info("Built itinerary request: destination={}, days={}, budget={}, partySize={}", 
            request.getDestinationName(), 
            request.getDurationDays(), 
            request.getTotalBudget(),
            request.getPartySize());
        
        return request;
    }
    
    @Override
    public String getToolName() {
        return "generate_itinerary";
    }
    
    @Override
    public String getDescription() {
        return "Generate a detailed travel itinerary based on destination, duration, budget, and preferences. " +
               "This tool will create a complete trip plan with daily activities, estimated costs, " +
               "and practical recommendations. The generation is asynchronous and may take a few seconds.";
    }
}
