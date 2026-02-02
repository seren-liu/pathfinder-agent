package com.travel.agent.controller;

import com.travel.agent.dto.response.CommonResponse;
import com.travel.agent.service.ItineraryEditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 行程编辑控制器
 * 
 * @author Blair
 * @since 2024-10-26
 */
@Slf4j
@RestController
@RequestMapping("/api/trips/{tripId}/editor")
@RequiredArgsConstructor
@Tag(name = "Itinerary Editor", description = "行程编辑API")
public class ItineraryEditorController {
    
    private final ItineraryEditService itineraryEditService;
    
    @PostMapping("/activities/{itemId}/move")
    @Operation(summary = "移动活动", description = "将活动移动到另一天")
    public CommonResponse<String> moveActivity(
            @PathVariable Long tripId,
            @PathVariable Long itemId,
            @RequestParam Long targetDayId,
            @RequestParam(required = false) String newStartTime,
            @RequestParam(required = false) String newEndTime
    ) {
        log.info("移动活动请求: tripId={}, itemId={}, targetDayId={}", tripId, itemId, targetDayId);
        return itineraryEditService.moveActivity(tripId, itemId, targetDayId, newStartTime, newEndTime);
    }
    
    @PostMapping("/activities")
    @Operation(summary = "添加活动", description = "添加新活动到指定天")
    public CommonResponse<?> addActivity(
            @PathVariable Long tripId,
            @RequestBody java.util.Map<String, Object> requestData
    ) {
        log.info("添加活动请求: tripId={}, data={}", tripId, requestData);
        
        Long dayId = requestData.get("dayId") != null ? 
                     ((Number) requestData.get("dayId")).longValue() : null;
        String activityName = (String) requestData.get("activityName");
        String activityType = (String) requestData.get("activityType");
        String startTime = (String) requestData.get("startTime");
        Integer durationMinutes = requestData.get("durationMinutes") != null ? 
                                 ((Number) requestData.get("durationMinutes")).intValue() : null;
        String location = (String) requestData.get("location");
        BigDecimal cost = requestData.get("cost") != null ? 
                          new BigDecimal(requestData.get("cost").toString()) : null;
        String notes = (String) requestData.get("notes");
        
        return itineraryEditService.addActivity(tripId, dayId, activityName, activityType, 
                                                startTime, durationMinutes, location, cost, notes);
    }
    
    @DeleteMapping("/activities/{itemId}")
    @Operation(summary = "删除活动", description = "删除指定活动")
    public CommonResponse<String> deleteActivity(
            @PathVariable Long tripId,
            @PathVariable Long itemId
    ) {
        log.info("删除活动请求: tripId={}, itemId={}", tripId, itemId);
        return itineraryEditService.deleteActivity(tripId, itemId);
    }
    
    @PutMapping("/activities/{itemId}")
    @Operation(summary = "更新活动", description = "更新指定活动的信息")
    public CommonResponse<?> updateActivity(
            @PathVariable Long tripId,
            @PathVariable Long itemId,
            @RequestBody java.util.Map<String, Object> requestData
    ) {
        log.info("更新活动请求: tripId={}, itemId={}, data={}", tripId, itemId, requestData);
        
        String activityName = (String) requestData.get("activityName");
        String activityType = (String) requestData.get("activityType");
        String startTime = (String) requestData.get("startTime");
        Integer durationMinutes = requestData.get("durationMinutes") != null ? 
                                   ((Number) requestData.get("durationMinutes")).intValue() : null;
        String location = (String) requestData.get("location");
        java.math.BigDecimal cost = requestData.get("cost") != null ? 
                                     new java.math.BigDecimal(requestData.get("cost").toString()) : null;
        String notes = (String) requestData.get("notes");
        
        return itineraryEditService.updateActivity(tripId, itemId, activityName, activityType, 
                                                    startTime, durationMinutes, location, cost, notes);
    }
    
    @PostMapping("/days")
    @Operation(summary = "添加新天", description = "为行程添加新的一天")
    public CommonResponse<?> addNewDay(
            @PathVariable Long tripId,
            @RequestBody(required = false) java.util.Map<String, Object> requestData
    ) {
        log.info("添加新天请求: tripId={}, data={}", tripId, requestData);
        
        try {
            if (requestData == null) {
                return CommonResponse.error(400, "Request body is required");
            }
            
            Integer dayNumber = requestData.get("dayNumber") != null ? 
                                ((Number) requestData.get("dayNumber")).intValue() : null;
            
            if (dayNumber == null) {
                return CommonResponse.error(400, "dayNumber is required");
            }
            
            String dateStr = (String) requestData.get("date");
            java.time.LocalDate date = null;
            if (dateStr != null && !dateStr.trim().isEmpty()) {
                try {
                    date = java.time.LocalDate.parse(dateStr);
                } catch (Exception e) {
                    log.warn("Failed to parse date: {}", dateStr, e);
                }
            }
            String theme = (String) requestData.get("theme");
            
            return itineraryEditService.addNewDay(tripId, dayNumber, date, theme);
        } catch (Exception e) {
            log.error("添加新天异常", e);
            return CommonResponse.error(500, "Failed to add new day: " + e.getMessage());
        }
    }
    
    @PutMapping("/days/{dayId}")
    @Operation(summary = "更新天的日期", description = "更新指定天的日期")
    public CommonResponse<?> updateDayDate(
            @PathVariable Long tripId,
            @PathVariable Long dayId,
            @RequestBody java.util.Map<String, Object> requestData
    ) {
        log.info("更新天的日期请求: tripId={}, dayId={}, data={}", tripId, dayId, requestData);
        
        try {
            String dateStr = (String) requestData.get("date");
            java.time.LocalDate date = null;
            if (dateStr != null && !dateStr.trim().isEmpty()) {
                try {
                    date = java.time.LocalDate.parse(dateStr);
                } catch (Exception e) {
                    log.warn("Failed to parse date: {}", dateStr, e);
                    return CommonResponse.error(400, "Invalid date format");
                }
            }
            
            return itineraryEditService.updateDayDate(tripId, dayId, date);
        } catch (Exception e) {
            log.error("更新天的日期异常", e);
            return CommonResponse.error(500, "Failed to update day date: " + e.getMessage());
        }
    }
    
    @DeleteMapping("/days/{dayId}")
    @Operation(summary = "删除天", description = "删除指定天及其所有活动")
    public CommonResponse<String> deleteDay(
            @PathVariable Long tripId,
            @PathVariable Long dayId
    ) {
        log.info("删除天请求: tripId={}, dayId={}", tripId, dayId);
        return itineraryEditService.deleteDay(tripId, dayId);
    }
    
    @PostMapping("/optimize")
    @Operation(summary = "AI优化", description = "AI智能分析和优化行程")
    public CommonResponse<?> optimizeItinerary(
            @PathVariable Long tripId,
            @RequestBody(required = false) java.util.Map<String, Object> requestData,
            @RequestParam(required = false, defaultValue = "general") String optimizationType
    ) {
        // 优先使用requestBody中的参数，如果没有则使用RequestParam
        String type = "general";
        if (requestData != null && requestData.get("optimizationType") != null) {
            type = (String) requestData.get("optimizationType");
        } else if (optimizationType != null) {
            type = optimizationType;
        }
        log.info("AI优化请求: tripId={}, type={}", tripId, type);
        return itineraryEditService.optimizeItinerary(tripId, type);
    }
    
    @PostMapping("/save")
    @Operation(summary = "保存编辑", description = "保存对行程的修改")
    public CommonResponse<String> saveEdit(
            @PathVariable Long tripId,
            @RequestParam(required = false) String editSummary
    ) {
        log.info("保存编辑请求: tripId={}", tripId);
        return itineraryEditService.saveEdit(tripId, editSummary);
    }
}

