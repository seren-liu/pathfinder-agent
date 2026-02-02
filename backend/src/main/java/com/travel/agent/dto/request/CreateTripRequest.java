package com.travel.agent.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Schema(description = "Create trip request")
public class CreateTripRequest {
    
    @NotNull(message = "User ID is required")
    @Schema(description = "User ID", required = true)
    private Long userId;
    
    @NotNull(message = "Destination ID is required")
    @Schema(description = "Destination ID", required = true)
    private Long destinationId;
    
    @Schema(description = "Start date (optional, can be set later)", example = "2025-10-20")
    private LocalDate startDate;
    
    @Schema(description = "End date (optional, can be set later)", example = "2025-10-24")
    private LocalDate endDate;
    
    @NotNull(message = "Duration days is required")
    @Min(value = 1, message = "Duration must be at least 1 day")
    @Schema(description = "Duration in days", required = true, example = "5")
    private Integer durationDays;
    
    @Schema(description = "Total budget in AUD", example = "1000.00")
    private BigDecimal totalBudget;
    
    @Schema(description = "Party size", example = "1")
    private Integer partySize = 1;
}
