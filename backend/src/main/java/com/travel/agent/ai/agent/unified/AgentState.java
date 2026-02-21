package com.travel.agent.ai.agent.unified;

import com.travel.agent.dto.AIDestinationRecommendation;
import com.travel.agent.dto.TravelIntent;
import com.travel.agent.dto.response.ParseIntentResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 状态
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentState implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long userId;
    private String sessionId;
    private String currentMessage;
    private List<String> conversationHistory;
    
    // 意图相关
    private TravelIntent intent;
    private ParseIntentResponse parsedIntent;
    
    // 推荐相关
    private List<AIDestinationRecommendation> recommendations;
    private AIDestinationRecommendation selectedDestination;
    
    // 行程相关
    private Long tripId;
    
    // 元数据
    private Map<String, Object> metadata;

    // 阶段状态（跨请求持久化）
    private AgentPhase phase;
    private Integer conversationTurns;
    private String lastAction;
    private LocalDateTime updatedAt;

    public enum AgentPhase {
        INITIAL,
        INTENT_EXTRACTED,
        COLLECTING_INFO,
        READY_FOR_RECOMMENDATION,
        RECOMMENDATION_READY,
        READY_FOR_ITINERARY,
        ITINERARY_STARTED,
        COMPLETED,
        FAILED
    }
    
    public static AgentState create(Long userId, String sessionId, String message) {
        return AgentState.builder()
            .userId(userId)
            .sessionId(sessionId)
            .currentMessage(message)
            .conversationHistory(new ArrayList<>())
            .metadata(new HashMap<>())
            .phase(AgentPhase.INITIAL)
            .conversationTurns(0)
            .updatedAt(LocalDateTime.now())
            .build();
    }
}
