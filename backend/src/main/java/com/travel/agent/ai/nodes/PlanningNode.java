package com.travel.agent.ai.nodes;

import com.travel.agent.ai.state.TravelPlanningState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 规划节点
 * 分解任务为具体步骤
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanningNode implements AsyncNodeAction<TravelPlanningState> {
    
    @Override
    public CompletableFuture<Map<String, Object>> apply(TravelPlanningState state) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("🎯 Planning Node: Breaking down the task for {}", state.getDestination());
            
            // 分解任务步骤
            List<String> steps = new ArrayList<>();
            steps.add("1. Search for real attractions in " + state.getDestination());
            steps.add("2. Validate budget constraints ($" + state.getBudget() + ")");
            steps.add("3. Geocode locations for map display");
            steps.add("4. Generate detailed " + state.getDurationDays() + "-day itinerary");
            steps.add("5. Validate itinerary quality");
            steps.add("6. Save to database");
            
            log.info("📋 Created {} planning steps", steps.size());
            
            // 更新状态
            return Map.of(
                "planSteps", steps,
                "currentStep", "Planning completed",
                "stepCount", 1,
                "progress", 10,
                "progressMessage", "Task planning completed"
            );
        });
    }
}
