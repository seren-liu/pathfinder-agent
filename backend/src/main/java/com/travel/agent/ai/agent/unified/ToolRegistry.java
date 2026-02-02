package com.travel.agent.ai.agent.unified;

import com.travel.agent.ai.agent.ActionResult;
import com.travel.agent.ai.agent.unified.tools.ConversationTool;
import com.travel.agent.ai.agent.unified.tools.ItineraryGenerationTool;
import com.travel.agent.ai.agent.unified.tools.RecommendationTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * 工具注册中心
 * 
 * 管理所有 Agent 工具，每个工具包装一个现有服务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolRegistry {
    
    private final ConversationTool conversationTool;
    private final RecommendationTool recommendationTool;
    private final ItineraryGenerationTool itineraryGenerationTool;
    
    private final Map<String, UnifiedAgentTool> tools = new HashMap<>();
    
    @PostConstruct
    public void init() {
        // 注册工具
        tools.put("conversation", conversationTool);
        tools.put("recommend_destinations", recommendationTool);
        tools.put("generate_itinerary", itineraryGenerationTool);
        
        log.info("✅ ToolRegistry initialized with {} tools", tools.size());
    }
    
    /**
     * 执行工具
     */
    public ActionResult execute(String toolName, AgentState state) {
        UnifiedAgentTool tool = tools.get(toolName);
        
        if (tool == null) {
            log.warn("Unknown tool: {}", toolName);
            return ActionResult.builder()
                .toolName(toolName)
                .success(false)
                .observation("Unknown tool: " + toolName)
                .build();
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            ActionResult result = tool.execute(state);
            result.setDurationMs(System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            log.error("Tool execution failed: {}", toolName, e);
            return ActionResult.builder()
                .toolName(toolName)
                .success(false)
                .observation("Error: " + e.getMessage())
                .error(e.getMessage())
                .durationMs(System.currentTimeMillis() - startTime)
                .build();
        }
    }
    
    /**
     * 获取所有工具
     */
    public Map<String, UnifiedAgentTool> getAllTools() {
        return new HashMap<>(tools);
    }
}
