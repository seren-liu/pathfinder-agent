package com.travel.agent.ai.agent.unified.tools;

import com.travel.agent.ai.agent.ActionResult;
import com.travel.agent.ai.agent.unified.AgentState;
import com.travel.agent.ai.agent.unified.UnifiedAgentTool;
import com.travel.agent.dto.AIDestinationRecommendation;
import com.travel.agent.dto.TravelIntent;
import com.travel.agent.dto.request.CreateTripRequest;
import com.travel.agent.dto.request.GenerateItineraryRequest;
import com.travel.agent.service.ItineraryGenerationService;
import com.travel.agent.service.TripsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 行程生成工具 - 包装现有 TripsService 和 ItineraryGenerationService
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItineraryGenerationTool implements UnifiedAgentTool {
    
    private final TripsService tripsService;
    private final ItineraryGenerationService itineraryGenerationService;
    
    @Override
    public ActionResult execute(AgentState state) {
        try {
            log.info("✈️ ItineraryGenerationTool executing for user: {}", state.getUserId());
            
            // 1. 构建 GenerateItineraryRequest
            GenerateItineraryRequest request = buildRequest(state);
            
            // 2. 创建 Trip 记录
            CreateTripRequest createTripRequest = new CreateTripRequest();
            createTripRequest.setUserId(state.getUserId());
            createTripRequest.setDurationDays(request.getDurationDays());
            createTripRequest.setTotalBudget(request.getTotalBudget());
            createTripRequest.setPartySize(request.getPartySize());
            
            Long tripId = tripsService.createTrip(createTripRequest);
            
            // 3. 异步生成行程
            itineraryGenerationService.generateItineraryAsync(tripId, request);
            
            return ActionResult.builder()
                .toolName("generate_itinerary")
                .success(true)
                .observation(String.format(
                        "已开始为你生成行程：%s，%d天，预算%s（行程ID：%d）。我会先给出基础版，再在后台继续优化路线。",
                        request.getDestinationName(),
                        request.getDurationDays(),
                        request.getTotalBudget(),
                        tripId
                ))
                .result(tripId)
                .build();
                
        } catch (Exception e) {
            log.error("ItineraryGenerationTool execution failed", e);
            return ActionResult.builder()
                .toolName("generate_itinerary")
                .success(false)
                .observation("Failed to generate itinerary: " + e.getMessage())
                .error(e.getMessage())
                .build();
        }
    }
    
    /**
     * 从 AgentState 构建 GenerateItineraryRequest
     */
    private GenerateItineraryRequest buildRequest(AgentState state) {
        TravelIntent intent = state.getIntent();
        AIDestinationRecommendation selectedDest = state.getSelectedDestination();
        
        if (intent == null) {
            throw new IllegalStateException("Intent is required for itinerary generation");
        }
        
        GenerateItineraryRequest request = new GenerateItineraryRequest();
        request.setUserId(state.getUserId());
        request.setSessionId(state.getSessionId());
        
        // 设置目的地信息
        if (selectedDest != null) {
            request.setDestinationName(selectedDest.getDestinationName());
            request.setDestinationCountry(selectedDest.getCountry());
            if (selectedDest.getLatitude() != null) {
                request.setDestinationLatitude(BigDecimal.valueOf(selectedDest.getLatitude()));
            }
            if (selectedDest.getLongitude() != null) {
                request.setDestinationLongitude(BigDecimal.valueOf(selectedDest.getLongitude()));
            }
        } else if (intent.getDestination() != null) {
            request.setDestinationName(intent.getDestination());
            request.setDestinationCountry("Unknown"); // 需要从其他地方获取
        } else {
            throw new IllegalStateException("Destination is required for itinerary generation");
        }
        
        // 设置天数
        if (intent.getDays() != null) {
            request.setDurationDays(intent.getDays());
        } else {
            request.setDurationDays(5); // 默认5天
        }
        
        // 设置预算
        if (intent.getBudget() != null) {
            try {
                String budgetStr = intent.getBudget().replaceAll("[^0-9]", "");
                request.setTotalBudget(new BigDecimal(budgetStr));
            } catch (NumberFormatException e) {
                log.warn("Invalid budget format: {}, using default", intent.getBudget());
                request.setTotalBudget(new BigDecimal("10000"));
            }
        } else {
            request.setTotalBudget(new BigDecimal("10000")); // 默认10000
        }
        
        // 设置人数
        request.setPartySize(2); // 默认2人
        
        // 设置偏好
        if (intent.getInterests() != null && !intent.getInterests().isEmpty()) {
            request.setPreferences(String.join(", ", intent.getInterests()));
        }
        
        log.info("Built itinerary request: destination={}, days={}, budget={}", 
            request.getDestinationName(), request.getDurationDays(), request.getTotalBudget());
        
        return request;
    }
    
    @Override
    public String getToolName() {
        return "generate_itinerary";
    }
    
    @Override
    public String getDescription() {
        return "Generate travel itinerary based on destination, duration, and budget";
    }
}
