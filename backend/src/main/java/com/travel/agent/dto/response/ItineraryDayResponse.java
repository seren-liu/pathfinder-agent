package com.travel.agent.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Schema(description = "单日行程响应")
public class ItineraryDayResponse {
    
    @Schema(description = "天数ID")
    private Long dayId;
    
    @Schema(description = "天数索引（1-5）")
    private Integer dayNumber;
    
    @Schema(description = "日期")
    private LocalDate date;
    
    @Schema(description = "当天主题")
    private String theme;
    
    @Schema(description = "当天活动列表")
    private List<ItineraryItemResponse> activities;
    
    @Schema(description = "当天总费用")
    private BigDecimal totalCost;
    
    @Schema(description = "当天总时长（分钟）")
    private Integer totalDuration;
    
    @Schema(description = "活动数量")
    private Integer activityCount;
}
