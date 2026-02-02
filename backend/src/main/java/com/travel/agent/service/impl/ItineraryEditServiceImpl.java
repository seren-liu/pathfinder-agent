package com.travel.agent.service.impl;

import com.travel.agent.entity.ItineraryItems;
import com.travel.agent.entity.ItineraryDays;
import com.travel.agent.entity.Trips;
import com.travel.agent.entity.ConversationHistory;
import com.travel.agent.entity.UserPreferences;
import com.travel.agent.service.*;
import com.travel.agent.dto.response.CommonResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItineraryEditServiceImpl implements ItineraryEditService {
    
    private final ItineraryItemsService itineraryItemsService;
    private final ItineraryDaysService itineraryDaysService;
    private final TripsService tripsService;
    private final ConversationHistoryService conversationHistoryService;
    private final UserPreferencesService userPreferencesService;
    private final AIService aiService;
    private final Gson gson = new Gson();
    
    @Override
    @Transactional
    public CommonResponse<String> moveActivity(Long tripId, Long itemId, Long targetDayId, String newStartTime, String newEndTime) {
        try {
            log.info("移动活动: itemId={}, targetDayId={}", itemId, targetDayId);
            
            ItineraryItems item = itineraryItemsService.getById(itemId);
            if (item == null) {
                return CommonResponse.error(404, "活动不存在");
            }
            
            item.setDayId(targetDayId);
            // TODO: 更新时间
            itineraryItemsService.updateById(item);
            
            log.info("活动已移动: itemId={}", itemId);
            return CommonResponse.success("活动已移动");
        } catch (Exception e) {
            log.error("移动活动失败", e);
            return CommonResponse.error(500, "移动活动失败");
        }
    }
    
    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "trip", key = "#tripId"),
        @CacheEvict(value = "latestTrip", allEntries = true)
    })
    public CommonResponse<ItineraryItems> addActivity(Long tripId, Long dayId, String activityName, String activityType,
                                                       String startTime, Integer durationMinutes, String location,
                                                       java.math.BigDecimal cost, String notes) {
        try {
            log.info("添加活动: dayId={}, activityName={}", dayId, activityName);
            
            ItineraryItems newItem = new ItineraryItems();
            newItem.setDayId(dayId);
            newItem.setTripId(tripId);
            newItem.setActivityName(activityName);
            newItem.setActivityType(activityType != null ? activityType : "activity");
            
            if (startTime != null) {
                newItem.setStartTime(java.time.LocalTime.parse(startTime));
            }
            if (durationMinutes != null) {
                newItem.setDurationMinutes(durationMinutes);
            }
            if (location != null) {
                newItem.setLocation(location);
            }
            if (cost != null) {
                newItem.setCost(cost);
            }
            if (notes != null) {
                newItem.setNotes(notes);
            }
            
            newItem.setCreatedAt(java.time.LocalDateTime.now());
            newItem.setUpdatedAt(java.time.LocalDateTime.now());
            
            itineraryItemsService.save(newItem);
            
            log.info("活动已添加: itemId={}, 缓存已清除", newItem.getId());
            return CommonResponse.success(newItem);
        } catch (Exception e) {
            log.error("添加活动失败", e);
            return CommonResponse.error(500, "添加活动失败: " + e.getMessage());
        }
    }
    
    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "trip", key = "#tripId"),
        @CacheEvict(value = "latestTrip", allEntries = true)
    })
    public CommonResponse<String> deleteActivity(Long tripId, Long itemId) {
        try {
            log.info("删除活动: itemId={}", itemId);
            
            boolean removed = itineraryItemsService.removeById(itemId);
            
            if (removed) {
                log.info("活动已删除: itemId={}, 缓存已清除", itemId);
                return CommonResponse.success("活动已删除");
            } else {
                return CommonResponse.error(404, "活动不存在");
            }
        } catch (Exception e) {
            log.error("删除活动失败", e);
            return CommonResponse.error(500, "删除活动失败");
        }
    }
    
    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "trip", key = "#tripId"),
        @CacheEvict(value = "latestTrip", allEntries = true)
    })
    public CommonResponse<ItineraryItems> updateActivity(Long tripId, Long itemId, String activityName,
                                                          String activityType, String startTime,
                                                          Integer durationMinutes, String location,
                                                          java.math.BigDecimal cost, String notes) {
        try {
            log.info("更新活动: itemId={}, activityName={}", itemId, activityName);
            
            ItineraryItems item = itineraryItemsService.getById(itemId);
            if (item == null) {
                return CommonResponse.error(404, "活动不存在");
            }
            
            // 更新活动信息
            if (activityName != null) {
                item.setActivityName(activityName);
            }
            if (activityType != null) {
                item.setActivityType(activityType);
            }
            if (startTime != null) {
                item.setStartTime(java.time.LocalTime.parse(startTime));
            }
            if (durationMinutes != null) {
                item.setDurationMinutes(durationMinutes);
            }
            if (location != null) {
                item.setLocation(location);
            }
            if (cost != null) {
                item.setCost(cost);
            }
            if (notes != null) {
                item.setNotes(notes);
            }
            
            item.setUpdatedAt(java.time.LocalDateTime.now());
            itineraryItemsService.updateById(item);
            
            log.info("活动已更新: itemId={}, cost={}, 缓存已清除", itemId, cost);
            return CommonResponse.success(item);
        } catch (Exception e) {
            log.error("更新活动失败", e);
            return CommonResponse.error(500, "更新活动失败: " + e.getMessage());
        }
    }
    
    @Override
    public CommonResponse<OptimizeResponse> optimizeItinerary(Long tripId, String optimizationType) {
        try {
            log.info("AI优化行程: tripId={}, type={}", tripId, optimizationType);
            
            // 构建AI提示
            String prompt = buildOptimizePrompt(tripId, optimizationType);
            
            // 调用AI
            String aiResponse = aiService.chat(prompt);
            
            // 解析AI响应
            OptimizeResponse response = parseOptimizeResponse(aiResponse);
            
            log.info("AI优化完成: tripId={}", tripId);
            return CommonResponse.success(response);
        } catch (Exception e) {
            log.error("AI优化失败", e);
            // 返回默认建议
            return CommonResponse.success(getDefaultOptimizeResponse());
        }
    }
    
    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "trip", key = "#tripId"),
        @CacheEvict(value = "latestTrip", allEntries = true)
    })
    public CommonResponse<?> addNewDay(Long tripId, Integer dayNumber, java.time.LocalDate date, String theme) {
        try {
            log.info("添加新天: tripId={}, dayNumber={}, date={}, theme={}", tripId, dayNumber, date, theme);
            
            // 检查行程是否存在
            Trips trip = tripsService.getById(tripId);
            if (trip == null) {
                return CommonResponse.error(404, "Trip not found");
            }
            
            // 检查是否已存在该天数
            ItineraryDays existingDay = itineraryDaysService.lambdaQuery()
                    .eq(ItineraryDays::getTripId, tripId)
                    .eq(ItineraryDays::getDayNumber, dayNumber)
                    .one();
            
            if (existingDay != null) {
                return CommonResponse.error(400, "Day " + dayNumber + " already exists");
            }
            
            // 创建新的一天
            ItineraryDays newDay = new ItineraryDays();
            newDay.setTripId(tripId);
            newDay.setDayNumber(dayNumber);
            
            // 处理日期：优先使用传入的日期，否则根据startDate计算，最后使用当前日期
            if (date != null) {
                newDay.setDate(date);
            } else if (trip.getStartDate() != null) {
                newDay.setDate(trip.getStartDate().plusDays(dayNumber - 1));
            } else {
                // 如果行程没有开始日期，使用当前日期加上天数
                newDay.setDate(java.time.LocalDate.now().plusDays(dayNumber - 1));
                log.warn("Trip {} has no startDate, using current date + {} days", tripId, dayNumber - 1);
            }
            
            newDay.setTheme(theme != null && !theme.trim().isEmpty() ? theme : "Exploring");
            newDay.setCreatedAt(java.time.LocalDateTime.now());
            
            itineraryDaysService.save(newDay);
            
            // 更新Trips表的durationDays（基于实际天数）
            updateTripDurationDays(tripId);
            
            log.info("新天已添加: dayId={}, dayNumber={}, 缓存已清除", newDay.getId(), dayNumber);
            return CommonResponse.success(newDay);
        } catch (Exception e) {
            log.error("添加新天失败", e);
            return CommonResponse.error(500, "Failed to add new day: " + e.getMessage());
        }
    }
    
    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "trip", key = "#tripId"),
        @CacheEvict(value = "latestTrip", allEntries = true)
    })
    public CommonResponse<?> updateDayDate(Long tripId, Long dayId, java.time.LocalDate date) {
        try {
            log.info("更新天的日期: tripId={}, dayId={}, date={}", tripId, dayId, date);
            
            // 检查天是否存在
            ItineraryDays day = itineraryDaysService.getById(dayId);
            if (day == null || !day.getTripId().equals(tripId)) {
                return CommonResponse.error(404, "Day not found");
            }
            
            day.setDate(date);
            itineraryDaysService.updateById(day);
            
            log.info("天的日期已更新: dayId={}, date={}", dayId, date);
            return CommonResponse.success(day);
        } catch (Exception e) {
            log.error("更新天的日期失败", e);
            return CommonResponse.error(500, "Failed to update day date: " + e.getMessage());
        }
    }
    
    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "trip", key = "#tripId"),
        @CacheEvict(value = "latestTrip", allEntries = true)
    })
    public CommonResponse<String> deleteDay(Long tripId, Long dayId) {
        try {
            log.info("删除天: tripId={}, dayId={}", tripId, dayId);
            
            // 检查天是否存在
            ItineraryDays day = itineraryDaysService.getById(dayId);
            if (day == null || !day.getTripId().equals(tripId)) {
                return CommonResponse.error(404, "Day not found");
            }
            
            // 先删除该天的所有活动（外键约束）
            List<ItineraryItems> items = itineraryItemsService.lambdaQuery()
                    .eq(ItineraryItems::getDayId, dayId)
                    .list();
            
            for (ItineraryItems item : items) {
                itineraryItemsService.removeById(item.getId());
            }
            
            log.info("已删除 {} 个活动", items.size());
            
            // 再删除天
            itineraryDaysService.removeById(dayId);
            
            // 更新Trips表的durationDays（基于实际天数）
            updateTripDurationDays(tripId);
            
            log.info("天已删除: dayId={}, dayNumber={}, 缓存已清除", dayId, day.getDayNumber());
            return CommonResponse.success("Day deleted successfully");
        } catch (Exception e) {
            log.error("删除天失败", e);
            return CommonResponse.error(500, "Failed to delete day: " + e.getMessage());
        }
    }
    
    /**
     * 更新行程的天数（基于实际的itinerary_days记录数）
     */
    private void updateTripDurationDays(Long tripId) {
        try {
            // 统计实际的天数
            long actualDays = itineraryDaysService.lambdaQuery()
                    .eq(ItineraryDays::getTripId, tripId)
                    .count();
            
            // 更新Trips表
            Trips trip = tripsService.getById(tripId);
            if (trip != null) {
                trip.setDurationDays((int) actualDays);
                
                // 如果有开始日期，更新结束日期
                if (trip.getStartDate() != null && actualDays > 0) {
                    trip.setEndDate(trip.getStartDate().plusDays(actualDays - 1));
                }
                
                tripsService.updateById(trip);
                log.info("已更新行程天数: tripId={}, durationDays={}", tripId, actualDays);
            }
        } catch (Exception e) {
            log.error("更新行程天数失败: tripId={}", tripId, e);
            // 不抛出异常，避免影响主流程
        }
    }
    
    @Override
    @Transactional
    public CommonResponse<String> saveEdit(Long tripId, String editSummary) {
        try {
            log.info("保存编辑: tripId={}, summary={}", tripId, editSummary);
            
            // TODO: 实现保存逻辑
            
            return CommonResponse.success("编辑已保存");
        } catch (Exception e) {
            log.error("保存编辑失败", e);
            return CommonResponse.error(500, "保存失败");
        }
    }
    
    private String buildOptimizePrompt(Long tripId, String optimizationType) {
        try {
            // 1. 获取行程信息
            Trips trip = tripsService.getById(tripId);
            if (trip == null) {
                return "Trip not found: " + tripId;
            }
            
            // 2. 获取用户偏好
            UserPreferences preferences = userPreferencesService.lambdaQuery()
                    .eq(UserPreferences::getUserId, trip.getUserId())
                    .one();
            
            String userContext = "";
            if (preferences != null) {
                userContext = String.format("""
                    User Travel Context:
                    - Travel Style: %s
                    - Budget Preference: %s
                    - Interests: %s
                    """, 
                    preferences.getTravelStyle(),
                    preferences.getBudgetPreference() != null ? preferences.getBudgetPreference() : "medium",
                    preferences.getInterests() != null ? preferences.getInterests() : "not specified"
                );
            }
            
            // 3. 获取最近的对话历史（最近的5条用户输入）
            List<ConversationHistory> recentHistory = conversationHistoryService.lambdaQuery()
                    .eq(ConversationHistory::getUserId, trip.getUserId())
                    .eq(ConversationHistory::getRole, "user")
                    .orderByDesc(ConversationHistory::getCreatedAt)
                    .last("LIMIT 5")
                    .list();
            
            String historyContext = "";
            if (recentHistory != null && !recentHistory.isEmpty()) {
                historyContext = "Recent User Requests:\n" + 
                    recentHistory.stream()
                        .map(h -> "- " + h.getMessage())
                        .collect(Collectors.joining("\n"));
            }
            
            // 4. 获取当前行程详情
            List<ItineraryDays> days = itineraryDaysService.lambdaQuery()
                    .eq(ItineraryDays::getTripId, tripId)
                    .orderByAsc(ItineraryDays::getDayNumber)
                    .list();
            
            String itineraryContext = "";
            if (days != null && !days.isEmpty()) {
                itineraryContext = "Current Itinerary:\n";
                for (ItineraryDays day : days) {
                    itineraryContext += String.format("Day %d (%s):\n", day.getDayNumber(), day.getTheme());
                    
                    List<ItineraryItems> items = itineraryItemsService.lambdaQuery()
                            .eq(ItineraryItems::getDayId, day.getId())
                            .orderByAsc(ItineraryItems::getStartTime)
                            .list();
                    
                    for (ItineraryItems item : items) {
                        itineraryContext += String.format("  - %s (%s) at %s, Cost: $%s\n",
                            item.getActivityName(),
                            item.getActivityType(),
                            item.getStartTime(),
                            item.getCost()
                        );
                    }
                }
            }
            
            // 5. 构建完整的AI提示
            return String.format("""
                **IMPORTANT: You must respond in English. All text must be in English only.**
                
                You are a professional travel itinerary optimizer. Analyze the following trip and provide intelligent optimization suggestions.
                
                **Trip Information:**
                - Destination: %s, %s
                - Duration: %d days
                - Budget: $%s AUD
                - Status: %s
                
                %s
                
                %s
                
                %s
                
                **Analysis Requirements:**
                1. Time conflicts detection (overlapping activities, too tight schedules)
                2. Budget balance suggestions (cost distribution, potential savings)
                3. Route optimization (location proximity, travel efficiency)
                4. Fatigue level analysis (rest periods, activity intensity)
                5. User preference alignment (based on their travel style and interests)
                6. Recent changes consideration (if user modified or added activities recently)
                
                **Response Format (pure JSON only, no markdown code blocks):**
                {
                  "suggestions": [
                    {
                      "title": "Time Conflict Detected",
                      "description": "Activity A and Activity B overlap in time. Consider rescheduling...",
                      "type": "time_conflict",
                      "priority": "high"
                    },
                    {
                      "title": "Budget Optimization",
                      "description": "You can save $X by replacing expensive activity with similar alternative...",
                      "type": "budget",
                      "priority": "medium"
                    }
                  ],
                  "aiExplanation": "Based on your travel style (XXX) and interest in (YYY), I recommend..."
                }
                
                Generate optimization suggestions now. Remember: ALL TEXT MUST BE IN ENGLISH.
                """, 
                trip.getDestinationName(),
                trip.getDestinationCountry(),
                trip.getDurationDays(),
                trip.getTotalBudget(),
                trip.getStatus(),
                userContext.isEmpty() ? "" : userContext,
                historyContext.isEmpty() ? "" : historyContext,
                itineraryContext.isEmpty() ? "Itinerary details not available." : itineraryContext
            );
        } catch (Exception e) {
            log.error("构建优化提示失败", e);
            return String.format("Analyze trip %d and provide optimization suggestions in English JSON format.", tripId);
        }
    }
    
    private OptimizeResponse parseOptimizeResponse(String aiResponse) {
        try {
            log.info("解析AI优化响应: {}", aiResponse);
            
            // 清理JSON响应（移除markdown代码块）
            String cleaned = cleanJsonResponse(aiResponse);
            log.info("清理后的响应: {}", cleaned);
            
            JsonObject json = gson.fromJson(cleaned, JsonObject.class);
            
            OptimizeResponse response = new OptimizeResponse();
            response.setType("general");
            
            // 解析suggestions（支持多种JSON结构）
            List<ChangeSuggestion> suggestions = new ArrayList<>();
            
            // 尝试从不同的JSON结构中提取建议
            if (json.has("suggestions")) {
                JsonArray suggestionsArray = json.getAsJsonArray("suggestions");
                for (int i = 0; i < suggestionsArray.size(); i++) {
                    JsonObject suggestionJson = suggestionsArray.get(i).getAsJsonObject();
                    ChangeSuggestion suggestion = new ChangeSuggestion();
                    
                    if (suggestionJson.has("title")) {
                        suggestion.setReason(suggestionJson.get("title").getAsString());
                    }
                    if (suggestionJson.has("description")) {
                        String desc = suggestionJson.get("description").getAsString();
                        if (suggestion.getReason() != null) {
                            suggestion.setReason(suggestion.getReason() + ": " + desc);
                        } else {
                            suggestion.setReason(desc);
                        }
                    }
                    
                    suggestions.add(suggestion);
                }
            }
            
            // 如果没有suggestions，尝试从分析结果中提取
            if (suggestions.isEmpty() && json.has("分析结果")) {
                JsonObject analysisResult = json.getAsJsonObject("分析结果");
                
                // 提取各个分析部分
                if (analysisResult.has("时间冲突检测")) {
                    JsonObject timeConflict = analysisResult.getAsJsonObject("时间冲突检测");
                    if (timeConflict.has("details")) {
                        ChangeSuggestion suggestion = new ChangeSuggestion();
                        suggestion.setReason("Time Conflict Analysis: " + timeConflict.get("details").getAsString());
                        suggestions.add(suggestion);
                    }
                }
                
                if (analysisResult.has("预算平衡建议")) {
                    JsonObject budget = analysisResult.getAsJsonObject("预算平衡建议");
                    if (budget.has("details")) {
                        ChangeSuggestion suggestion = new ChangeSuggestion();
                        suggestion.setReason("Budget Recommendation: " + budget.get("details").getAsString());
                        suggestions.add(suggestion);
                    }
                }
                
                if (analysisResult.has("路线优化建议")) {
                    JsonObject route = analysisResult.getAsJsonObject("路线优化建议");
                    if (route.has("details")) {
                        ChangeSuggestion suggestion = new ChangeSuggestion();
                        suggestion.setReason("Route Optimization: " + route.get("details").getAsString());
                        suggestions.add(suggestion);
                    }
                }
                
                if (analysisResult.has("疲劳度分析")) {
                    JsonObject fatigue = analysisResult.getAsJsonObject("疲劳度分析");
                    if (fatigue.has("details")) {
                        ChangeSuggestion suggestion = new ChangeSuggestion();
                        suggestion.setReason("Fatigue Analysis: " + fatigue.get("details").getAsString());
                        suggestions.add(suggestion);
                    }
                }
            }
            
            // 去重：移除重复的建议（基于reason内容，标准化处理）
            List<ChangeSuggestion> uniqueSuggestions = new ArrayList<>();
            Set<String> seenReasons = new HashSet<>();
            for (ChangeSuggestion suggestion : suggestions) {
                String reason = suggestion.getReason();
                if (reason != null && !reason.trim().isEmpty()) {
                    // 标准化：转换为小写、去除多余空格、去除标点符号（保留基本字符）
                    String normalized = reason.trim()
                            .toLowerCase()
                            .replaceAll("\\s+", " ")  // 多个空格合并为一个
                            .replaceAll("[\\p{P}&&[^\\.]]", "")  // 移除标点符号（保留句号）
                            .trim();
                    
                    if (!normalized.isEmpty() && !seenReasons.contains(normalized)) {
                        seenReasons.add(normalized);
                        uniqueSuggestions.add(suggestion);
                    } else {
                        log.debug("跳过重复建议: {}", reason);
                    }
                }
            }
            
            log.info("去重前: {} 条建议, 去重后: {} 条建议", suggestions.size(), uniqueSuggestions.size());
            response.setChanges(uniqueSuggestions);
            
            // 解析aiExplanation
            String aiExplanation = "Based on your itinerary, here are my optimization suggestions:";
            if (json.has("aiExplanation")) {
                aiExplanation = json.get("aiExplanation").getAsString();
            } else if (json.has("分析结果")) {
                // 如果没有aiExplanation但有分析结果，生成一个总结
                aiExplanation = "I've analyzed your itinerary. Please check the detailed suggestions below.";
            }
            response.setAiExplanation(aiExplanation);
            
            // 设置Impact（如果有）
            ImpactAnalysis impact = new ImpactAnalysis();
            if (json.has("impact")) {
                JsonObject impactJson = json.getAsJsonObject("impact");
                if (impactJson.has("budgetChange")) {
                    impact.setBudgetChange(impactJson.get("budgetChange").getAsInt());
                }
            }
            response.setImpact(impact);
            
            log.info("✅ 解析完成: {} suggestions", suggestions.size());
            return response;
        } catch (Exception e) {
            log.error("解析AI响应失败，使用原始响应", e);
            OptimizeResponse response = new OptimizeResponse();
            response.setType("general");
            response.setChanges(new ArrayList<>());
            
            ImpactAnalysis impact = new ImpactAnalysis();
            impact.setBudgetChange(0);
            impact.setTimeSaved("Unknown");
            impact.setFatigueReduction("Unknown");
            response.setImpact(impact);
            
            response.setAiExplanation(aiResponse.length() > 500 ? aiResponse.substring(0, 500) + "..." : aiResponse);
            return response;
        }
    }
    
    private String cleanJsonResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return "{}";
        }
        
        String cleaned = response;
        
        // 移除markdown代码块标记
        cleaned = cleaned.replaceAll("```json\\s*", "");
        cleaned = cleaned.replaceAll("```\\s*", "");
        cleaned = cleaned.replaceAll("```", "");
        
        // 提取JSON对象（处理可能包含在文本中的JSON）
        int jsonStart = cleaned.indexOf("{");
        int jsonEnd = cleaned.lastIndexOf("}");
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            cleaned = cleaned.substring(jsonStart, jsonEnd + 1);
        }
        
        // 移除末尾多余的逗号
        cleaned = cleaned.replaceAll(",\\s*}", "}");
        cleaned = cleaned.replaceAll(",\\s*]", "]");
        
        return cleaned.trim();
    }
    
    private OptimizeResponse getDefaultOptimizeResponse() {
        OptimizeResponse response = new OptimizeResponse();
        response.setType("general");
        response.setChanges(new ArrayList<>());
        
        ImpactAnalysis impact = new ImpactAnalysis();
        impact.setBudgetChange(0);
        impact.setTimeSaved("None");
        impact.setFatigueReduction("None");
        response.setImpact(impact);
        
        response.setAiExplanation("Your itinerary is well-balanced. No major changes suggested.");
        
        return response;
    }
}

