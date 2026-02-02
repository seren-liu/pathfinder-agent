package com.travel.agent.ai.nodes;

import com.travel.agent.ai.agent.ReActTravelAgent;
import com.travel.agent.ai.state.TravelPlanningState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * ReAct 行程生成节点
 * 使用 ReAct Agent 自主推理和执行工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReActItineraryNode implements AsyncNodeAction<TravelPlanningState> {
    
    private final ReActTravelAgent reactAgent;
    
    @Override
    public CompletableFuture<Map<String, Object>> apply(TravelPlanningState state) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("🤖 ReAct Itinerary Node: Starting autonomous agent execution");
            
            try {
                // 执行 ReAct Agent
                TravelPlanningState resultState = reactAgent.execute(state);
                
                // 提取结果
                Map<String, Object> updates = new HashMap<>();
                
                if (resultState.getAttractions() != null) {
                    updates.put("attractions", resultState.getAttractions());
                }
                
                if (resultState.getBudgetCheck() != null) {
                    updates.put("budgetCheck", resultState.getBudgetCheck());
                }
                
                if (resultState.getItinerary() != null) {
                    updates.put("itinerary", resultState.getItinerary());
                }
                
                if (resultState.getMetadata() != null) {
                    updates.put("metadata", resultState.getMetadata());
                }
                
                updates.put("currentStep", "ReAct agent completed");
                updates.put("progress", 60);
                updates.put("progressMessage", "ReAct agent finished autonomous execution");
                
                log.info("✅ ReAct Itinerary Node: Agent execution completed");
                
                return updates;
                
            } catch (Exception e) {
                log.error("❌ ReAct Itinerary Node: Execution failed", e);
                return Map.of(
                    "errorMessage", "ReAct agent failed: " + e.getMessage(),
                    "currentStep", "ReAct agent failed"
                );
            }
        });
    }
}
