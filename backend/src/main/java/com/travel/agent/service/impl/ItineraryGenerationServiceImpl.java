package com.travel.agent.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.agent.dto.request.GenerateItineraryRequest;
import com.travel.agent.entity.*;
import com.travel.agent.exception.BusinessException;
import com.travel.agent.service.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItineraryGenerationServiceImpl implements ItineraryGenerationService {
    
    private final AIService aiService;
    private final TripsService tripsService;
    private final ItineraryDaysService itineraryDaysService;
    private final ItineraryItemsService itineraryItemsService;
    private final DestinationsService destinationsService;
    private final GeoapifyService geoapifyService;  // 保留用于附近地点搜索
    private final MapboxGeocodingService mapboxGeocodingService;  // 新增：Mapbox 地理编码
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final StateMachineItineraryService stateMachineService;  // 新增：状态机服务
    
    /**
     * 异步生成行程（核心方法）
     */
    @Override
    @Async("taskExecutor")
    @Transactional(rollbackFor = Exception.class)
    public CompletableFuture<Void> generateItineraryAsync(Long tripId, GenerateItineraryRequest request) {
        try {
            log.info("🚀 Starting itinerary generation: tripId={}", tripId);
            
            // ✅ 方案 B: 使用请求中的目的地信息，不依赖 destinationId
            updateProgress(tripId, 10, "Preparing destination information...");
            
            String destinationName = request.getDestinationName();
            String destinationCountry = request.getDestinationCountry();
            BigDecimal latitude = request.getDestinationLatitude();
            BigDecimal longitude = request.getDestinationLongitude();
            
            // 如果提供了 destinationId，尝试从数据库获取更完整的信息
            Destinations destination = null;
            if (request.getDestinationId() != null) {
                destination = destinationsService.getById(request.getDestinationId());
                if (destination != null) {
                    log.info("Using destination from database: {}", destination.getName());
                    destinationName = destination.getName();
                    destinationCountry = destination.getCountry();
                    latitude = destination.getLatitude();
                    longitude = destination.getLongitude();
                }
            }
            
            log.info("Destination: {} ({}), Lat: {}, Lon: {}", 
                destinationName, destinationCountry, latitude, longitude);
            
            // 2. 调用 OpenAI 生成行程框架
            updateProgress(tripId, 30, "AI is planning your itinerary...");
            String aiPrompt = buildOptimizedPrompt(destinationName, destinationCountry, request);
            String aiResponse = aiService.chat(aiPrompt);
            
            // 3. 解析 AI 响应
            updateProgress(tripId, 50, "Parsing itinerary data...");
            List<DayPlan> dayPlans = parseAIResponse(aiResponse, request);
            
            // 4. 快速保存到数据库（不地理编码）
            updateProgress(tripId, 70, "Saving itinerary...");
            saveItineraryToDatabase(tripId, request, dayPlans, destinationName, latitude, longitude);
            
            // 5. 更新 Trip 状态为 completed（让用户可以立即查看）
            Trips trip = tripsService.getById(tripId);
            trip.setStatus("completed");
            tripsService.updateById(trip);
            
            // 清除缓存
            evictTripCache(tripId);
            
            // ✅ 行程生成完成
            updateProgress(tripId, 100, "Itinerary ready!");
            log.info("✅ Itinerary saved successfully: tripId={}", tripId);
            
        } catch (Exception e) {
            log.error("Itinerary generation failed: tripId={}, error={}", tripId, e.getMessage(), e);
            
            String errorMessage = e.getMessage();
            if (errorMessage == null || errorMessage.isEmpty()) {
                errorMessage = "Unknown error occurred";
            }
            updateProgress(tripId, 0, "Generation failed: " + errorMessage);
            
            // 更新 Trip 状态为 planning（失败后回到规划状态）
            try {
                Trips trip = tripsService.getById(tripId);
                if (trip != null) {
                    trip.setStatus("planning");  // 使用数据库中已存在的状态
                    tripsService.updateById(trip);
                }
            } catch (Exception ex) {
                log.error("Failed to update trip status", ex);
            }
            
            return CompletableFuture.completedFuture(null);
        }
        
        // ✅ 事务已提交，status='completed' 立即可见，用户可以跳转
        // 6. 异步地理编码（后台执行，不阻塞用户）
        CompletableFuture.runAsync(() -> {
            try {
                log.info("🗺️ Starting background geocoding for trip: {}", tripId);
                geocodeActivitiesSync(tripId);
                evictTripCache(tripId);
                log.info("✅ Background geocoding completed for trip: {}", tripId);
            } catch (Exception e) {
                log.error("⚠️ Background geocoding failed for trip: {}", tripId, e);
            }
        });
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * 构建优化的 Prompt（精简版，降低 Token 消耗）
     * ✅ 方案 B: 使用目的地名称和国家，不依赖 Destinations 对象
     */
    private String buildOptimizedPrompt(String destinationName, String destinationCountry, GenerateItineraryRequest request) {
        return String.format("""
            Generate a %d-day travel itinerary for %s, %s.
            Budget: $%s AUD, Party size: %d
            
            CRITICAL: Return ONLY valid JSON (no markdown, no extra text). Ensure the JSON is COMPLETE.
            
            Requirements:
            - Each day: 4 activities (accommodation, dining, activity, transportation)
            - Activity names: MUST be specific real places with exact names
            - Location format: MUST include street address or specific landmark
              * Good: "The Ranee Boutique Suites, Jalan Tunku Abdul Rahman, Kuching, Malaysia"
              * Bad: "Kuching, Borneo, Malaysia" (too vague)
            - Start times: realistic (08:00, 12:00, 18:00, etc.)
            - Costs: reasonable for budget
            
            JSON Format:
            {
              "days": [
                {
                  "dayNumber": 1,
                  "theme": "Arrival Day",
                  "activities": [
                    {"name": "Hilton Hotel", "type": "accommodation", "startTime": "14:00", "durationMinutes": 60, "estimatedCost": 150, "location": "Hilton Hotel, Main Street 123, %s, %s"},
                    {"name": "Seaside Restaurant", "type": "dining", "startTime": "19:00", "durationMinutes": 90, "estimatedCost": 50, "location": "Seaside Restaurant, Harbor Road 45, %s, %s"}
                  ]
                }
              ]
            }
            
            IMPORTANT: Every activity must have a unique, specific street address or landmark name. Do NOT use only city names.
            
            Generate complete %d-day itinerary (JSON only, ensure it ends with }):
            """,
            request.getDurationDays(),
            destinationName,
            destinationCountry,
            request.getTotalBudget(),
            request.getPartySize(),
            destinationName,
            destinationCountry,
            destinationName,
            destinationCountry,
            request.getDurationDays()
        );
    }
    
    /**
     * 解析 AI 响应
     */
    private List<DayPlan> parseAIResponse(String aiResponse, GenerateItineraryRequest request) {
        try {
            // 清理可能的 markdown 代码块标记
            String cleanedResponse = aiResponse.trim();
            if (cleanedResponse.startsWith("```json")) {
                cleanedResponse = cleanedResponse.substring(7);
            }
            if (cleanedResponse.startsWith("```")) {
                cleanedResponse = cleanedResponse.substring(3);
            }
            if (cleanedResponse.endsWith("```")) {
                cleanedResponse = cleanedResponse.substring(0, cleanedResponse.length() - 3);
            }
            cleanedResponse = cleanedResponse.trim();
            
            log.info("AI Response length: {} characters", cleanedResponse.length());
            log.debug("AI Response preview: {}...", cleanedResponse.substring(0, Math.min(200, cleanedResponse.length())));
            
            // 检查 JSON 是否完整
            if (!cleanedResponse.endsWith("}")) {
                log.error("AI response appears incomplete. Last 100 chars: {}", 
                    cleanedResponse.substring(Math.max(0, cleanedResponse.length() - 100)));
                throw new BusinessException("AI response is incomplete. Please try again or reduce the number of days.");
            }
            
            JsonNode root = objectMapper.readTree(cleanedResponse);
            JsonNode daysNode = root.get("days");
            
            if (daysNode == null || !daysNode.isArray()) {
                log.error("Invalid AI response format: missing 'days' array");
                throw new BusinessException("Invalid AI response format: missing 'days' array");
            }
            
            List<DayPlan> dayPlans = new ArrayList<>();
            
            for (JsonNode dayNode : daysNode) {
                DayPlan dayPlan = new DayPlan();
                dayPlan.setDayNumber(dayNode.get("dayNumber").asInt());
                dayPlan.setTheme(dayNode.get("theme").asText());
                
                List<ActivityPlan> activities = new ArrayList<>();
                JsonNode activitiesNode = dayNode.get("activities");
                
                if (activitiesNode != null && activitiesNode.isArray()) {
                    for (JsonNode activityNode : activitiesNode) {
                        ActivityPlan activity = new ActivityPlan();
                        activity.setName(activityNode.get("name").asText());
                        activity.setType(activityNode.get("type").asText());
                        activity.setStartTime(activityNode.get("startTime").asText());
                        activity.setDurationMinutes(activityNode.get("durationMinutes").asInt());
                        activity.setEstimatedCost(new BigDecimal(activityNode.get("estimatedCost").asText()));
                        
                        // 获取 location（可能为空）
                        if (activityNode.has("location")) {
                            activity.setLocation(activityNode.get("location").asText());
                        }
                        
                        activities.add(activity);
                    }
                }
                
                dayPlan.setActivities(activities);
                dayPlans.add(dayPlan);
            }
            
            log.info("✅ Parsed {} days with {} total activities", 
                dayPlans.size(), 
                dayPlans.stream().mapToInt(d -> d.getActivities().size()).sum());
            
            return dayPlans;
            
        } catch (Exception e) {
            log.error("Failed to parse AI response: {}", e.getMessage(), e);
            throw new BusinessException("Failed to parse AI response: " + e.getMessage());
        }
    }
    
    /**
     * 保存行程到数据库
     * ✅ 方案 B: 使用目的地名称和坐标，不依赖 Destinations 对象
     */
    private void saveItineraryToDatabase(Long tripId, GenerateItineraryRequest request, 
                                        List<DayPlan> dayPlans, String destinationName, 
                                        BigDecimal latitude, BigDecimal longitude) {
        // ✅ 修复：如果没有提供 startDate，date 字段设为 null
        LocalDate currentDate = request.getStartDate();
        
        for (DayPlan dayPlan : dayPlans) {
            // 创建 ItineraryDay
            ItineraryDays day = new ItineraryDays();
            day.setTripId(tripId);
            day.setDayNumber(dayPlan.getDayNumber());
            day.setDate(currentDate);  // 可能为 null，用户后续设置
            day.setTheme(dayPlan.getTheme());
            itineraryDaysService.save(day);
            
            // 创建 ItineraryItems
            int orderIndex = 0;
            for (ActivityPlan activity : dayPlan.getActivities()) {
                ItineraryItems item = new ItineraryItems();
                item.setTripId(tripId);
                item.setDayId(day.getId());
                item.setOrderIndex(orderIndex++);
                item.setActivityName(activity.getName());
                item.setActivityType(activity.getType());
                
                try {
                    item.setStartTime(LocalTime.parse(activity.getStartTime()));
                } catch (Exception e) {
                    log.warn("Invalid time format: {}, using default", activity.getStartTime());
                    item.setStartTime(LocalTime.of(9, 0));
                }
                
                item.setDurationMinutes(activity.getDurationMinutes());
                String location = activity.getLocation() != null ? activity.getLocation() : destinationName;
                item.setLocation(location);
                
                // ✅ 不设置坐标，保持为 null
                // 地理编码会在事务提交后立即执行，前端轮询会获取到准确坐标
                item.setLatitude(null);
                item.setLongitude(null);
                
                item.setCost(activity.getEstimatedCost());
                item.setStatus("planned");
                item.setOriginalFlag(true);
                itineraryItemsService.save(item);
            }
            
            // 只有当 startDate 不为 null 时才递增日期
            if (currentDate != null) {
                currentDate = currentDate.plusDays(1);
            }
        }
        
        log.info("✅ Saved {} days to database", dayPlans.size());
    }
    
    /**
     * 更新生成进度（存储到 Redis）
     */
    @Override
    public void updateProgress(Long tripId, Integer progress, String step) {
        String key = "trip:generation:" + tripId;
        Map<String, Object> progressData = new HashMap<>();
        progressData.put("progress", progress);
        progressData.put("step", step);
        progressData.put("timestamp", System.currentTimeMillis());
        
        redisTemplate.opsForHash().putAll(key, progressData);
        redisTemplate.expire(key, 10, TimeUnit.MINUTES);
        
        log.info("📊 Progress updated: tripId={}, progress={}%, step={}", tripId, progress, step);
    }
    
    /**
     * 获取生成进度
     */
    @Override
    public Integer getGenerationProgress(Long tripId) {
        String key = "trip:generation:" + tripId;
        Object progress = redisTemplate.opsForHash().get(key, "progress");
        return progress != null ? Integer.parseInt(progress.toString()) : 0;
    }
    
    /**
     * 获取当前步骤描述
     */
    @Override
    public String getCurrentStep(Long tripId) {
        String key = "trip:generation:" + tripId;
        Object step = redisTemplate.opsForHash().get(key, "step");
        return step != null ? step.toString() : "Initializing...";
    }
    
    /**
     * 清除行程缓存（同时清除最新行程缓存）
     */
    @Caching(evict = {
        @CacheEvict(value = "trip", key = "#tripId"),
        @CacheEvict(value = "latestTrip", allEntries = true)
    })
    public void evictTripCache(Long tripId) {
        log.info("🗑️ Evicting cache for trip: {} and all latestTrip cache", tripId);
    }
    
    /**
     * 并行地理编码所有活动（使用 Mapbox，高性能版本）
     */
    private void geocodeActivitiesSync(Long tripId) {
        long startTime = System.currentTimeMillis();
        log.info("🗺️ Starting parallel geocoding for trip: {} (using Mapbox)", tripId);
        
        // 获取所有活动
        List<ItineraryItems> items = itineraryItemsService.list(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ItineraryItems>()
                .eq(ItineraryItems::getTripId, tripId)
        );
        
        // 使用并行流进行地理编码（最多10个并发）
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        
        // 收集需要更新的项（避免 N+1 问题）
        List<ItineraryItems> itemsToUpdate = new java.util.concurrent.CopyOnWriteArrayList<>();
        
        // 并行处理，最多10个线程
        items.parallelStream()
            .limit(items.size())
            .forEach(item -> {
                try {
                    String location = item.getLocation();
                    if (location != null && !location.isEmpty()) {
                        // ✅ 使用 Mapbox 替代 Geoapify
                        Map<String, BigDecimal> coordinates = mapboxGeocodingService.geocodeAddress(location);
                        
                        if (coordinates != null) {
                            item.setLatitude(coordinates.get("latitude"));
                            item.setLongitude(coordinates.get("longitude"));
                            itemsToUpdate.add(item);  // 收集而不是立即更新
                            successCount.incrementAndGet();
                            log.debug("✅ Geocoded (Mapbox): {} -> lat={}, lon={}", 
                                item.getActivityName(), coordinates.get("latitude"), coordinates.get("longitude"));
                        } else {
                            // ⚠️ Mapbox 失败，尝试 Geoapify 作为备用
                            log.warn("⚠️ Mapbox geocoding failed for: {}, trying Geoapify fallback", location);
                            Map<String, BigDecimal> fallbackCoords = geoapifyService.geocodeAddress(location);
                            if (fallbackCoords != null) {
                                item.setLatitude(fallbackCoords.get("latitude"));
                                item.setLongitude(fallbackCoords.get("longitude"));
                                itemsToUpdate.add(item);  // 收集而不是立即更新
                                successCount.incrementAndGet();
                                log.debug("✅ Geocoded (Geoapify fallback): {}", item.getActivityName());
                            } else {
                                failCount.incrementAndGet();
                                log.debug("⚠️ Geocoding failed for: {}", location);
                            }
                        }
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    log.debug("Failed to geocode activity: {}", item.getActivityName(), e);
                }
            });
        
        // 批量更新数据库（一次性更新所有项，避免 N+1 问题）
        if (!itemsToUpdate.isEmpty()) {
            itineraryItemsService.updateBatchById(itemsToUpdate);
            log.info("📦 Batch updated {} items to database", itemsToUpdate.size());
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        log.info("✅ Parallel geocoding completed (Mapbox): {} success, {} failed, duration: {}ms", 
            successCount.get(), failCount.get(), duration);
    }
    
    // 内部类：行程计划
    @Data
    private static class DayPlan {
        private Integer dayNumber;
        private String theme;
        private List<ActivityPlan> activities;
    }
    
    @Data
    private static class ActivityPlan {
        private String name;
        private String type;
        private String startTime;
        private Integer durationMinutes;
        private BigDecimal estimatedCost;
        private String location;
    }
}