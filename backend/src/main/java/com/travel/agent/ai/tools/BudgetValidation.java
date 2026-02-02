package com.travel.agent.ai.tools;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/**
 * 预算验证结果
 */
@Data
@Builder
public class BudgetValidation {
    private BigDecimal totalCost;
    private BigDecimal budget;
    private Boolean withinBudget;
    private BigDecimal remaining;
    private List<CostItem> costBreakdown;
    private List<String> recommendations;
}
