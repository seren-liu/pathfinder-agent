package com.travel.agent.ai.agent.unified.tools;

import com.travel.agent.ai.agent.ActionResult;
import com.travel.agent.ai.agent.unified.AgentState;
import com.travel.agent.ai.agent.unified.UnifiedAgentTool;
import com.travel.agent.dto.response.ChatResponse;
import com.travel.agent.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 对话工具 - 包装现有 ConversationService
 * 
 * 不重写业务逻辑，只是提供 Agent 工具接口
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationTool implements UnifiedAgentTool {
    
    private final ConversationService conversationService;
    
    @Override
    public ActionResult execute(AgentState state) {
        try {
            log.info("🗣️ ConversationTool executing for session: {}", state.getSessionId());
            
            // 调用现有服务（userId 是 Long 类型）
            ChatResponse response = conversationService.chat(
                state.getUserId(), 
                state.getSessionId(), 
                state.getCurrentMessage()
            );
            
            return ActionResult.builder()
                .toolName("conversation")
                .success(true)
                .observation("AI response: " + response.getMessage())
                .result(response)
                .build();
                
        } catch (Exception e) {
            log.error("ConversationTool execution failed", e);
            return ActionResult.builder()
                .toolName("conversation")
                .success(false)
                .observation("Failed to process conversation: " + e.getMessage())
                .error(e.getMessage())
                .build();
        }
    }
    
    @Override
    public String getToolName() {
        return "conversation";
    }
    
    @Override
    public String getDescription() {
        return "Chat with user to collect information or provide responses";
    }
}
