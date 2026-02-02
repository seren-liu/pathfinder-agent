package com.travel.agent.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Schema(description = "Generate itinerary request")
public class GenerateItineraryRequest {
    
    @NotNull(message = "User ID is required")
    @Schema(description = "User ID", required = true)
    private Long userId;
    
    // ✅ 方案 B: destinationId 改为可选
    @Schema(description = "Destination ID (optional, for reference only)")
    private Long destinationId;
    
    // ✅ 方案 B: 添加目的地信息字段（必填）
    @NotNull(message = "Destination name is required")
    @Schema(description = "Destination name", required = true)
    private String destinationName;
    
    @NotNull(message = "Destination country is required")
    @Schema(description = "Destination country", required = true)
    private String destinationCountry;
    
    @Schema(description = "Destination latitude")
    private BigDecimal destinationLatitude;
    
    @Schema(description = "Destination longitude")
    private BigDecimal destinationLongitude;
    
    @Schema(description = "Start date (optional, can be set later)")
    private LocalDate startDate;
    
    @Schema(description = "End date (optional, can be set later)")
    private LocalDate endDate;
    
    @NotNull(message = "Duration days is required")
    @Schema(description = "Duration in days", required = true)
    private Integer durationDays;
    
    @Schema(description = "Total budget in AUD")
    private BigDecimal totalBudget;
    
    @Schema(description = "Party size")
    private Integer partySize = 1;
    
    @Schema(description = "User preferences from profile")
    private String preferences;
    
    @Schema(description = "Chat session ID (from intelligent dialogue)")
    private String sessionId;
}
