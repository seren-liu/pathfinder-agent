package com.travel.agent.ai.state;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 预算检查结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetCheck {
    private BigDecimal totalCost;
    private BigDecimal budget;
    private Boolean withinBudget;
    private BigDecimal remaining;
    private List<String> recommendations;
    
    /**
     * 从 Map 转换（使用 Builder 模式）
     */
    @SuppressWarnings("unchecked")
    public static BudgetCheck fromMap(Map<String, Object> map) {
        if (map == null) return null;

        BudgetCheckBuilder builder = BudgetCheck.builder()
                .withinBudget((Boolean) map.get("withinBudget"))
                .recommendations((List<String>) map.get("recommendations"));

        Object totalCost = map.get("totalCost");
        if (totalCost instanceof BigDecimal) {
            builder.totalCost((BigDecimal) totalCost);
        } else if (totalCost instanceof Number) {
            builder.totalCost(new BigDecimal(totalCost.toString()));
        }

        Object budget = map.get("budget");
        if (budget instanceof BigDecimal) {
            builder.budget((BigDecimal) budget);
        } else if (budget instanceof Number) {
            builder.budget(new BigDecimal(budget.toString()));
        }

        Object remaining = map.get("remaining");
        if (remaining instanceof BigDecimal) {
            builder.remaining((BigDecimal) remaining);
        } else if (remaining instanceof Number) {
            builder.remaining(new BigDecimal(remaining.toString()));
        }

        return builder.build();
    }
}
