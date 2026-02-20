package com.travel.agent.ai.nodes;

import com.travel.agent.ai.state.TravelPlanningState;
import com.travel.agent.monitoring.RouteOptimizationMetrics;
import com.travel.agent.service.MapboxGeocodingService;
import com.travel.agent.service.RouteOptimizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 路线优化节点
 * 在行程生成之后、质量反思之前执行
 * 对每天的活动进行 TSP 求解，优化访问顺序
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RouteOptimizationNode implements AsyncNodeAction<TravelPlanningState> {

    private final MapboxGeocodingService geocodingService;
    private final RouteOptimizationService routeOptimizationService;
    private final RouteOptimizationMetrics metrics;

    @Override
    public CompletableFuture<Map<String, Object>> apply(TravelPlanningState state) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("🚀 Route Optimization Node: Optimizing routes for {}-day itinerary",
                    state.getItinerary() != null ? state.getItinerary().size() : 0);
            
            // 📊 记录优化尝试
            metrics.recordOptimizationAttempt();

            try {
                List<Map<String, Object>> itinerary = state.getItinerary();
                if (itinerary == null || itinerary.isEmpty()) {
                    log.warn("⚠️ No itinerary to optimize");
                    Map<String, Object> result = new HashMap<>();
                    result.put("progress", 75);
                    result.put("progressMessage", "Route optimization skipped: no itinerary");
                    return result;
                }

                String destination = state.getDestination();
                String country = state.getDestinationCountry();
                String destinationContext = destination
                        + (country != null ? ", " + country : "");

                // Phase 1: 收集所有活动的唯一地点
                Set<String> uniqueLocations = new LinkedHashSet<>();
                for (Map<String, Object> dayPlan : itinerary) {
                    List<Map<String, Object>> activities = getActivities(dayPlan);
                    if (activities != null) {
                        for (Map<String, Object> activity : activities) {
                            String location = (String) activity.get("location");
                            if (location != null && !location.isBlank()) {
                                String fullLocation = location + ", " + destinationContext;
                                uniqueLocations.add(fullLocation);
                            }
                        }
                    }
                }

                if (uniqueLocations.isEmpty()) {
                    log.warn("No locations found in itinerary activities");
                    Map<String, Object> result = new HashMap<>();
                    result.put("progress", 75);
                    result.put("progressMessage", "Route optimization skipped: no locations");
                    return result;
                }

                // Phase 2: 批量地理编码
                Map<String, Map<String, BigDecimal>> geoResults =
                        geocodingService.batchGeocode(new ArrayList<>(uniqueLocations));

                log.info("Geocoded {}/{} locations successfully",
                        geoResults.size(), uniqueLocations.size());

                // Phase 3: 构建 geoData 状态数据
                Map<String, Map<String, Object>> geoData = new HashMap<>();
                for (Map.Entry<String, Map<String, BigDecimal>> entry : geoResults.entrySet()) {
                    Map<String, Object> coordMap = new HashMap<>();
                    coordMap.put("latitude", entry.getValue().get("latitude"));
                    coordMap.put("longitude", entry.getValue().get("longitude"));
                    coordMap.put("location", entry.getKey());
                    coordMap.put("success", true);
                    geoData.put(entry.getKey(), coordMap);
                }

                // Phase 4: 逐天优化路线（需要至少有 geocoding 结果才进行优化）
                List<Map<String, Object>> optimizedItinerary = new ArrayList<>();
                int daysOptimized = 0;

                if (!geoResults.isEmpty()) {
                    for (Map<String, Object> dayPlan : itinerary) {
                        Map<String, Object> optimizedDay = new HashMap<>(dayPlan);
                        List<Map<String, Object>> activities = getActivities(dayPlan);

                        if (activities != null && activities.size() >= 3) {
                            List<Map<String, Object>> optimized =
                                    routeOptimizationService.optimizeDayRoute(
                                            activities, geoResults, destinationContext);
                            optimizedDay.put("activities", optimized);
                            daysOptimized++;
                        }
                        optimizedItinerary.add(optimizedDay);
                    }
                } else {
                    optimizedItinerary.addAll(itinerary);
                }

                log.info("✅ Route optimization completed: {}/{} days optimized, {} locations geocoded",
                        daysOptimized, itinerary.size(), geoData.size());
                
                // 📊 记录优化成功
                metrics.recordOptimizationSuccess();
                
                Map<String, Object> result = new HashMap<>();
                result.put("itinerary", optimizedItinerary);
                result.put("geoData", geoData);
                result.put("progress", 75);
                result.put("progressMessage", String.format("Route optimization completed: %d/%d days optimized, %d locations geocoded",
                        daysOptimized, itinerary.size(), geoData.size()));
                return result;
                
            } catch (Exception e) {
                log.error("❌ Route optimization failed, returning original itinerary", e);
                
                // 📊 记录优化失败
                metrics.recordOptimizationFailure();
                
                Map<String, Object> result = new HashMap<>();
                result.put("itinerary", state.getItinerary());
                result.put("progress", 75);
                result.put("progressMessage", "Route optimization skipped due to error");
                return result;
            }
        });
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getActivities(Map<String, Object> dayPlan) {
        Object activitiesObj = dayPlan.get("activities");
        if (activitiesObj instanceof List) {
            return (List<Map<String, Object>>) activitiesObj;
        }
        return null;
    }
}
