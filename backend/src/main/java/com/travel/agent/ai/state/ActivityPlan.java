package com.travel.agent.ai.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 活动计划
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityPlan {
    private String name;
    private String type;
    private String location;
    private String startTime;
    private Integer durationMinutes;
    private BigDecimal estimatedCost;
    private String description;
    private String notes;
    
    /**
     * 从 Map 转换
     */
    public static ActivityPlan fromMap(Map<String, Object> map) {
        if (map == null) return null;
        
        ActivityPlan activity = new ActivityPlan();
        activity.setName((String) map.get("name"));
        activity.setType((String) map.get("type"));
        activity.setLocation((String) map.get("location"));
        activity.setStartTime((String) map.get("startTime"));
        activity.setDurationMinutes((Integer) map.get("durationMinutes"));
        activity.setDescription((String) map.get("description"));
        activity.setNotes((String) map.get("notes"));
        
        Object cost = map.get("estimatedCost");
        if (cost instanceof BigDecimal) {
            activity.setEstimatedCost((BigDecimal) cost);
        } else if (cost instanceof Number) {
            activity.setEstimatedCost(new BigDecimal(cost.toString()));
        }
        
        return activity;
    }
}
