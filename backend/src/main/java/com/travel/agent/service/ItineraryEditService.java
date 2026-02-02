package com.travel.agent.service;

import com.travel.agent.entity.ItineraryItems;
import com.travel.agent.dto.response.CommonResponse;

import java.util.List;

/**
 * 行程编辑服务
 * 
 * @author Blair
 * @since 2024-10-26
 */
public interface ItineraryEditService {
    
    /**
     * 移动活动到另一天
     */
    CommonResponse<String> moveActivity(Long tripId, Long itemId, Long targetDayId, String newStartTime, String newEndTime);
    
    /**
     * 添加新活动
     */
    CommonResponse<ItineraryItems> addActivity(Long tripId, Long dayId, String activityName, String activityType, 
                                                String startTime, Integer durationMinutes, String location,
                                                java.math.BigDecimal cost, String notes);
    
    /**
     * 删除活动
     */
    CommonResponse<String> deleteActivity(Long tripId, Long itemId);
    
    /**
     * 更新活动
     */
    CommonResponse<ItineraryItems> updateActivity(Long tripId, Long itemId, String activityName, 
                                                    String activityType, String startTime, 
                                                    Integer durationMinutes, String location, 
                                                    java.math.BigDecimal cost, String notes);
    
    /**
     * AI优化行程
     */
    CommonResponse<OptimizeResponse> optimizeItinerary(Long tripId, String optimizationType);
    
    /**
     * 添加新的一天
     */
    CommonResponse<?> addNewDay(Long tripId, Integer dayNumber, java.time.LocalDate date, String theme);
    
    /**
     * 更新天的日期
     */
    CommonResponse<?> updateDayDate(Long tripId, Long dayId, java.time.LocalDate date);
    
    /**
     * 删除某一天
     */
    CommonResponse<String> deleteDay(Long tripId, Long dayId);
    
    /**
     * 保存编辑
     */
    CommonResponse<String> saveEdit(Long tripId, String editSummary);
    
    /**
     * 优化响应
     */
    class OptimizeResponse {
        private String type;
        private List<ChangeSuggestion> changes;
        private ImpactAnalysis impact;
        private String aiExplanation;
        
        // Getters and setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public List<ChangeSuggestion> getChanges() { return changes; }
        public void setChanges(List<ChangeSuggestion> changes) { this.changes = changes; }
        public ImpactAnalysis getImpact() { return impact; }
        public void setImpact(ImpactAnalysis impact) { this.impact = impact; }
        public String getAiExplanation() { return aiExplanation; }
        public void setAiExplanation(String aiExplanation) { this.aiExplanation = aiExplanation; }
    }
    
    class ChangeSuggestion {
        private Long itemId;
        private String originalTime;
        private String suggestedTime;
        private String reason;
        
        // Getters and setters
        public Long getItemId() { return itemId; }
        public void setItemId(Long itemId) { this.itemId = itemId; }
        public String getOriginalTime() { return originalTime; }
        public void setOriginalTime(String originalTime) { this.originalTime = originalTime; }
        public String getSuggestedTime() { return suggestedTime; }
        public void setSuggestedTime(String suggestedTime) { this.suggestedTime = suggestedTime; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
    
    class ImpactAnalysis {
        private Integer budgetChange;
        private String timeSaved;
        private String fatigueReduction;
        
        // Getters and setters
        public Integer getBudgetChange() { return budgetChange; }
        public void setBudgetChange(Integer budgetChange) { this.budgetChange = budgetChange; }
        public String getTimeSaved() { return timeSaved; }
        public void setTimeSaved(String timeSaved) { this.timeSaved = timeSaved; }
        public String getFatigueReduction() { return fatigueReduction; }
        public void setFatigueReduction(String fatigueReduction) { this.fatigueReduction = fatigueReduction; }
    }
}

