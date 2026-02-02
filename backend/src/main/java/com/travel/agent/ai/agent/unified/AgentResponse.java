package com.travel.agent.ai.agent.unified;

import com.travel.agent.ai.agent.ReActStep;
import com.travel.agent.dto.AIDestinationRecommendation;
import com.travel.agent.dto.TravelIntent;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Agent 响应
 */
@Data
@Builder
public class AgentResponse {
    private String actionType; // "chat", "recommend", "generate", "complete"
    private String message;
    private TravelIntent intent;
    private List<AIDestinationRecommendation> recommendations;
    private Long tripId;
    private List<ReActStep> reasoningHistory;
    private Map<String, Object> metadata;
}
