package com.travel.agent.controller;

import com.travel.agent.ai.agent.unified.AgentResponse;
import com.travel.agent.ai.agent.unified.UnifiedReActAgent;
import com.travel.agent.dto.response.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 统一 Agent API 控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@Tag(name = "Agent", description = "Unified ReAct Agent API")
public class AgentController {
    
    private final UnifiedReActAgent agent;
    
    /**
     * 统一的 Agent 聊天接口
     * 
     * Agent 会自主决定：
     * - 继续对话收集信息
     * - 推荐目的地
     * - 生成行程
     */
    @PostMapping("/chat")
    @Operation(summary = "Chat with unified ReAct Agent", 
               description = "Agent autonomously decides next action: conversation, recommendation, or itinerary generation")
    public CommonResponse<AgentResponse> chat(
            @RequestParam Long userId,
            @RequestParam(required = false) String sessionId,
            @RequestBody String message) {
        
        try {
            log.info("📨 Agent chat request: userId={}, sessionId={}, message='{}'", 
                    userId, sessionId, message);
            
            // 如果没有 sessionId，Agent 会创建新的
            if (sessionId == null || sessionId.isEmpty()) {
                sessionId = java.util.UUID.randomUUID().toString();
            }
            
            AgentResponse response = agent.execute(userId, sessionId, message);
            
            log.info("✅ Agent response: actionType={}, tripId={}", 
                    response.getActionType(), response.getTripId());
            
            return CommonResponse.success(response);
        } catch (Exception e) {
            log.error("Agent execution failed", e);
            return CommonResponse.error("Agent execution failed: " + e.getMessage());
        }
    }
}
