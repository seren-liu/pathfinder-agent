package com.travel.agent.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Chat request")
public class ChatRequest {
    
    @Schema(description = "User ID", required = true)
    private Long userId;
    
    @Schema(description = "Session ID (optional, will create new if not provided)")
    private String sessionId;
    
    @Schema(description = "User message", required = true)
    private String message;
}
