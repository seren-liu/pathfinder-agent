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
     * 从 Map 转换（使用 Builder 模式）
     */
    public static ActivityPlan fromMap(Map<String, Object> map) {
        if (map == null) return null;

        ActivityPlanBuilder builder = ActivityPlan.builder()
                .name((String) map.get("name"))
                .type((String) map.get("type"))
                .location((String) map.get("location"))
                .startTime((String) map.get("startTime"))
                .durationMinutes((Integer) map.get("durationMinutes"))
                .description((String) map.get("description"))
                .notes((String) map.get("notes"));

        Object cost = map.get("estimatedCost");
        if (cost instanceof BigDecimal) {
            builder.estimatedCost((BigDecimal) cost);
        } else if (cost instanceof Number) {
            builder.estimatedCost(new BigDecimal(cost.toString()));
        }

        return builder.build();
    }
}
