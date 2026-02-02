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
     * 从 Map 转换
     */
    @SuppressWarnings("unchecked")
    public static BudgetCheck fromMap(Map<String, Object> map) {
        if (map == null) return null;
        
        BudgetCheck check = new BudgetCheck();
        check.setWithinBudget((Boolean) map.get("withinBudget"));
        check.setRecommendations((List<String>) map.get("recommendations"));
        
        Object totalCost = map.get("totalCost");
        if (totalCost instanceof BigDecimal) {
            check.setTotalCost((BigDecimal) totalCost);
        } else if (totalCost instanceof Number) {
            check.setTotalCost(new BigDecimal(totalCost.toString()));
        }
        
        Object budget = map.get("budget");
        if (budget instanceof BigDecimal) {
            check.setBudget((BigDecimal) budget);
        } else if (budget instanceof Number) {
            check.setBudget(new BigDecimal(budget.toString()));
        }
        
        Object remaining = map.get("remaining");
        if (remaining instanceof BigDecimal) {
            check.setRemaining((BigDecimal) remaining);
        } else if (remaining instanceof Number) {
            check.setRemaining(new BigDecimal(remaining.toString()));
        }
        
        return check;
    }
}
