package com.travel.agent.ai.tools;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

/**
 * POI (Point of Interest) DTO
 */
@Data
@Builder
public class POI {
    private String name;
    private String category;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String address;
    private Double distance;  // 距离中心点的距离（米）
}
