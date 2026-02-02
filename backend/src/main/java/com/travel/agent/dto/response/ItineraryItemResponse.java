package com.travel.agent.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalTime;

@Data
@Schema(description = "行程活动响应")
public class ItineraryItemResponse {
    
    @Schema(description = "活动 ID")
    private Long activityId;
    
    @Schema(description = "活动名称")
    private String activityName;
    
    @Schema(description = "活动类型：accommodation/dining/activity/transportation/other")
    private String activityType;
    
    @Schema(description = "开始时间")
    private LocalTime startTime;
    
    @Schema(description = "持续时间（分钟）")
    private Integer durationMinutes;
    
    @Schema(description = "地点")
    private String location;
    
    @Schema(description = "GPS 纬度")
    private BigDecimal latitude;
    
    @Schema(description = "GPS 经度")
    private BigDecimal longitude;
    
    @Schema(description = "费用（AUD）")
    private BigDecimal cost;
    
    @Schema(description = "预订链接")
    private String bookingUrl;
    
    @Schema(description = "状态：planned/completed/cancelled")
    private String status;
    
    @Schema(description = "备注")
    private String notes;
    
    @Schema(description = "是否为原始活动")
    private Boolean originalFlag;
    
    @Schema(description = "Geoapify Place ID")
    private String placeId;
    
    @Schema(description = "排序索引")
    private Integer orderIndex;
    
    @Schema(description = "天数索引（用于前端显示）")
    private Integer dayNumber;
}
