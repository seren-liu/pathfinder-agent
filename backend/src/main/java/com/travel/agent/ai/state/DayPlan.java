package com.travel.agent.ai.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 单日行程计划
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DayPlan {
    private Integer dayNumber;
    private String theme;
    private String date;
    private List<ActivityPlan> activities;
    
    /**
     * 从 Map 转换
     */
    @SuppressWarnings("unchecked")
    public static DayPlan fromMap(Map<String, Object> map) {
        if (map == null) return null;
        
        DayPlan dayPlan = new DayPlan();
        dayPlan.setDayNumber((Integer) map.get("dayNumber"));
        dayPlan.setTheme((String) map.get("theme"));
        dayPlan.setDate((String) map.get("date"));
        
        Object activitiesObj = map.get("activities");
        if (activitiesObj instanceof List) {
            List<Map<String, Object>> activityMaps = (List<Map<String, Object>>) activitiesObj;
            List<ActivityPlan> activities = activityMaps.stream()
                .map(ActivityPlan::fromMap)
                .toList();
            dayPlan.setActivities(activities);
        }
        
        return dayPlan;
    }
}
