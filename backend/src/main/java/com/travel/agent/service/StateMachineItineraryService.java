package com.travel.agent.service;

import com.travel.agent.ai.graph.TravelPlanningGraph;
import com.travel.agent.ai.state.TravelPlanningState;
import com.travel.agent.dto.request.GenerateItineraryRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 基于状态机的行程生成服务
 * 使用 LangGraph4j 编排复杂的多步骤工作流
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StateMachineItineraryService {
    
    private final TravelPlanningGraph travelPlanningGraph;
    private final RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 使用状态机生成行程
     */
    public CompletableFuture<TravelPlanningState> generateItinerary(
            Long tripId, 
            GenerateItineraryRequest request) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("🚀 Starting state machine itinerary generation for trip: {}", tripId);
                
                // 构建状态图
                CompiledGraph<TravelPlanningState> graph = travelPlanningGraph.buildGraph();
                
                // 初始化状态
                Map<String, Object> initialState = new HashMap<>();
                initialState.put("tripId", tripId);
                initialState.put("destination", request.getDestinationName());
                initialState.put("destinationCountry", request.getDestinationCountry());
                initialState.put("durationDays", request.getDurationDays());
                initialState.put("budget", request.getTotalBudget());
                initialState.put("partySize", request.getPartySize());
                initialState.put("preferences", request.getPreferences());
                initialState.put("startDate", request.getStartDate() != null 
                    ? request.getStartDate().toString() 
                    : null);
                initialState.put("destinationLatitude", request.getDestinationLatitude());
                initialState.put("destinationLongitude", request.getDestinationLongitude());
                
                // 执行状态图
                log.info("▶️ Invoking state graph...");
                var resultOpt = graph.invoke(initialState);
                
                // 获取最终状态
                TravelPlanningState finalState = resultOpt.orElseThrow(
                    () -> new RuntimeException("State graph execution returned empty result")
                );
                
                log.info("✅ State machine execution completed");
                log.info("📊 Final state: progress={}, approved={}, reflectionCount={}", 
                        finalState.getProgress(),
                        finalState.getApproved(),
                        finalState.getReflectionCount());
                
                // 更新 Redis 进度
                updateProgress(tripId, finalState.getProgress(), finalState.getProgressMessage());
                
                return finalState;
                
            } catch (Exception e) {
                log.error("❌ State machine execution failed for trip: {}", tripId, e);
                log.error("❌ Exception type: {}", e.getClass().getName());
                log.error("❌ Exception message: {}", e.getMessage());
                if (e.getCause() != null) {
                    log.error("❌ Cause: {}", e.getCause().getMessage());
                    log.error("❌ Cause type: {}", e.getCause().getClass().getName());
                }
                
                // 打印完整堆栈
                log.error("❌ Full stack trace:", e);
                
                // 更新错误状态
                updateProgress(tripId, 0, "Generation failed: " + e.getMessage());
                
                throw new RuntimeException("State machine execution failed: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * 更新进度到 Redis
     */
    private void updateProgress(Long tripId, Integer progress, String message) {
        try {
            String progressKey = "itinerary:progress:" + tripId;
            Map<String, Object> progressData = new HashMap<>();
            progressData.put("progress", progress);
            progressData.put("message", message);
            progressData.put("timestamp", System.currentTimeMillis());
            
            redisTemplate.opsForValue().set(progressKey, progressData, 1, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Failed to update progress in Redis", e);
        }
    }
}
