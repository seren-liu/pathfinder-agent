package com.travel.agent.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(name = "ChatSessionSummaryResponse", description = "Chat session summary")
public class ChatSessionSummaryResponse {

    private String sessionId;
    private String title;
    private String lastMessage;
    private Integer messageCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

