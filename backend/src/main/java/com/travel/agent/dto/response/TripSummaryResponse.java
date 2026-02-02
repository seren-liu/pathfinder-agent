package com.travel.agent.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 行程摘要响应（用于列表展示）
 *
 * @author Blair
 * @since 2025-10-29
 */
@Data
@Schema(description = "行程摘要响应")
public class TripSummaryResponse {
    
    @Schema(description = "行程 ID")
    private Long tripId;
    
    @Schema(description = "目的地名称")
    private String destinationName;
    
    @Schema(description = "目的地国家")
    private String destinationCountry;
    
    @Schema(description = "开始日期")
    private LocalDate startDate;
    
    @Schema(description = "结束日期")
    private LocalDate endDate;
    
    @Schema(description = "行程天数")
    private Integer durationDays;
    
    @Schema(description = "总预算（AUD）")
    private BigDecimal totalBudget;
    
    @Schema(description = "状态：planning/generating/completed")
    private String status;
    
    @Schema(description = "创建时间")
    private java.time.LocalDateTime createdAt;
}

