package com.travel.agent.ai.agent.unified.tools;

import com.travel.agent.ai.agent.ActionResult;
import com.travel.agent.ai.agent.unified.UnifiedAgentState;
import com.travel.agent.ai.agent.unified.UnifiedAgentTool;
import com.travel.agent.dto.response.ChatResponse;
import com.travel.agent.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 统一对话工具
 * 
 * 改进点：
 * 1. 使用 UnifiedAgentState
 * 2. 更新对话历史到状态
 * 3. 详细的日志记录
 */
@Slf4j
@Component("unifiedConversationTool")
@RequiredArgsConstructor
public class UnifiedConversationTool implements UnifiedAgentTool {
    
    private final ConversationService conversationService;
    
    @Override
    public ActionResult execute(UnifiedAgentState state) {
        try {
            log.info("🗣️ UnifiedConversationTool executing for session: {}", state.getSessionId());
            
            // 验证状态
            if (state.getCurrentMessage() == null || state.getCurrentMessage().isEmpty()) {
                return ActionResult.builder()
                    .toolName(getToolName())
                    .success(false)
                    .observation("No message to process")
                    .error("Current message is empty")
                    .build();
            }
            
            // 调用对话服务
            ChatResponse response = conversationService.chat(
                state.getUserId(), 
                state.getSessionId(), 
                state.getCurrentMessage()
            );
            
            // 更新对话历史
            state.addConversationMessage("user", state.getCurrentMessage());
            state.addConversationMessage("assistant", response.getMessage());
            
            // 更新执行阶段
            state.setCurrentPhase(UnifiedAgentState.ExecutionPhase.CONVERSING);
            
            log.info("✅ Conversation completed, response length: {}", response.getMessage().length());
            
            return ActionResult.builder()
                .toolName(getToolName())
                .success(true)
                .observation("AI response: " + response.getMessage())
                .result(response)
                .build();
                
        } catch (Exception e) {
            log.error("UnifiedConversationTool execution failed", e);
            state.addError("Conversation failed: " + e.getMessage());
            
            return ActionResult.builder()
                .toolName(getToolName())
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
        return "Chat with user to collect travel information or provide responses. " +
               "This tool will engage in natural conversation to understand user's preferences, " +
               "answer questions, and guide the user through the travel planning process.";
    }
}
