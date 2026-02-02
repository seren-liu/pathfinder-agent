package com.travel.agent.dto.response;

import com.travel.agent.dto.TravelIntent;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    
    @Schema(description = "Session ID")
    private String sessionId;
    
    @Schema(description = "AI response message")
    private String message;
    
    @Schema(description = "Response timestamp")
    private LocalDateTime timestamp;
    private TravelIntent intent;  // 意图信息（首次对话时返回）
}
