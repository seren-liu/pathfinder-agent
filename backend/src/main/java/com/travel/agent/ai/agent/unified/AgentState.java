package com.travel.agent.ai.agent.unified;

import com.travel.agent.dto.AIDestinationRecommendation;
import com.travel.agent.dto.TravelIntent;
import com.travel.agent.dto.response.ParseIntentResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
public class AgentState {
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
    
    public static AgentState create(Long userId, String sessionId, String message) {
        return AgentState.builder()
            .userId(userId)
            .sessionId(sessionId)
            .currentMessage(message)
            .conversationHistory(new ArrayList<>())
            .metadata(new HashMap<>())
            .build();
    }
}
