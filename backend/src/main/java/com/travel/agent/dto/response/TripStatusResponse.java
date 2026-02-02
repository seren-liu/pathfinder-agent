package com.travel.agent.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Trip generation status response")
public class TripStatusResponse {
    
    @Schema(description = "Trip ID")
    private Long tripId;
    
    @Schema(description = "Status: generating/completed/failed")
    private String status;
    
    @Schema(description = "Progress percentage (0-100)")
    private Integer progress;
    
    @Schema(description = "Current step description")
    private String currentStep;
    
    @Schema(description = "Error message if failed")
    private String errorMessage;
}
