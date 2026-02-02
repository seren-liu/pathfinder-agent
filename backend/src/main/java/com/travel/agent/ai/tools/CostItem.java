package com.travel.agent.ai.tools;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 成本项
 */
@Data
@Builder
public class CostItem {
    private String name;
    private String category;
    private BigDecimal cost;
}
