package com.travel.agent.ai.nodes;

import com.travel.agent.ai.state.TravelPlanningState;
import com.travel.agent.entity.ItineraryDays;
import com.travel.agent.entity.ItineraryItems;
import com.travel.agent.entity.Trips;
import com.travel.agent.service.ItineraryDaysService;
import com.travel.agent.service.ItineraryItemsService;
import com.travel.agent.service.TripsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 保存节点
 * 将行程保存到数据库
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SaveNode implements AsyncNodeAction<TravelPlanningState> {
    
    private final TripsService tripsService;
    private final ItineraryDaysService itineraryDaysService;
    private final ItineraryItemsService itineraryItemsService;
    
    @Override
    public CompletableFuture<Map<String, Object>> apply(TravelPlanningState state) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("💾 Save Node: Saving itinerary to database");
            
            try {
                // 保存行程到数据库
                saveToDatabase(state);
                
                // 更新 Trip 状态
                Trips trip = tripsService.getById(state.getTripId());
                if (trip != null) {
                    trip.setStatus("completed");
                    tripsService.updateById(trip);
                }
                
                log.info("✅ Itinerary saved successfully");
                
                Map<String, Object> result = new HashMap<>();
                result.put("currentStep", "Completed");
                result.put("progress", 100);
                result.put("progressMessage", "Itinerary saved successfully!");
                return result;
                
            } catch (Exception e) {
                log.error("❌ Save failed", e);
                throw new RuntimeException("Save failed: " + e.getMessage(), e);
            }
        });
    }
    
    private void saveToDatabase(TravelPlanningState state) {
        if (state.getItinerary() == null || state.getItinerary().isEmpty()) {
            log.warn("No itinerary to save");
            return;
        }
        
        LocalDate startDate = state.getStartDate() != null && !state.getStartDate().isEmpty()
            ? LocalDate.parse(state.getStartDate())
            : LocalDate.now();
        
        for (Map<String, Object> dayPlan : state.getItinerary()) {
            // 创建 ItineraryDay
            ItineraryDays day = new ItineraryDays();
            day.setTripId(state.getTripId());
            day.setDayNumber(((Number) dayPlan.get("dayNumber")).intValue());
            day.setTheme((String) dayPlan.get("theme"));
            day.setDate(startDate.plusDays(day.getDayNumber() - 1));
            itineraryDaysService.save(day);
            
            // 创建 ItineraryItems
            Object activitiesObj = dayPlan.get("activities");
            if (activitiesObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> activities = (List<Map<String, Object>>) activitiesObj;
                
                int orderIndex = 1;
                for (Map<String, Object> activity : activities) {
                    ItineraryItems item = new ItineraryItems();
                    item.setTripId(state.getTripId());
                    item.setDayId(day.getId());
                    item.setActivityName((String) activity.get("name"));
                    item.setActivityType(normalizeActivityType((String) activity.get("type")));
                    item.setLocation((String) activity.get("location"));
                    
                    // 处理时间
                    if (activity.get("startTime") != null) {
                        try {
                            item.setStartTime(LocalTime.parse((String) activity.get("startTime")));
                        } catch (Exception e) {
                            log.warn("Failed to parse start time: {}", activity.get("startTime"));
                        }
                    }
                    
                    // 处理时长
                    if (activity.get("durationMinutes") != null) {
                        item.setDurationMinutes(((Number) activity.get("durationMinutes")).intValue());
                    }
                    
                    // 处理成本
                    if (activity.get("estimatedCost") != null) {
                        item.setCost(new BigDecimal(activity.get("estimatedCost").toString()));
                    }
                    
                    item.setOrderIndex(orderIndex++);

                    // 从 geoData 查找地理编码坐标并持久化
                    Map<String, Map<String, Object>> geoData = state.getGeoData();
                    String activityLocation = (String) activity.get("location");
                    if (activityLocation != null && geoData != null && !geoData.isEmpty()) {
                        String fullKey = activityLocation + ", " + state.getDestination()
                                + (state.getDestinationCountry() != null
                                    ? ", " + state.getDestinationCountry() : "");
                        Map<String, Object> coords = geoData.get(fullKey);
                        if (coords == null) {
                            coords = geoData.get(activityLocation);
                        }
                        if (coords != null) {
                            Object lat = coords.get("latitude");
                            Object lon = coords.get("longitude");
                            if (lat instanceof BigDecimal) {
                                item.setLatitude((BigDecimal) lat);
                            } else if (lat instanceof Number) {
                                item.setLatitude(new BigDecimal(lat.toString()));
                            }
                            if (lon instanceof BigDecimal) {
                                item.setLongitude((BigDecimal) lon);
                            } else if (lon instanceof Number) {
                                item.setLongitude(new BigDecimal(lon.toString()));
                            }
                        }
                    }

                    itineraryItemsService.save(item);
                }
            }
        }
    }

    /**
     * Normalize activity type to fit DB constraint:
     * transportation/accommodation/dining/activity/other
     */
    private String normalizeActivityType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return "activity";
        }

        String type = rawType.trim().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "transportation", "accommodation", "dining", "activity", "other" -> type;
            case "hotel", "lodging", "stay", "checkin", "check-in" -> "accommodation";
            case "restaurant", "food", "cafe", "breakfast", "lunch", "dinner" -> "dining";
            case "transit", "transfer", "flight", "train", "bus", "taxi", "metro", "subway", "drive" -> "transportation";
            case "sightseeing", "landmark", "museum", "attraction", "culture", "shopping", "entertainment", "experience", "outdoor", "nature" -> "activity";
            default -> "activity";
        };
    }
}
