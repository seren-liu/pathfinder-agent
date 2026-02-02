package com.travel.agent.ai.agent.unified;

import com.travel.agent.ai.agent.ActionResult;
import com.travel.agent.ai.agent.ReActStep;
import com.travel.agent.monitoring.AgentMetricsService;
import com.travel.agent.service.AIService;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 统一的 ReAct Agent 入口
 * 
 * 复用企业级基础设施：
 * - AgentMetricsService（阶段6）：监控指标
 * - ToolRegistry：工具编排
 * - 现有服务：ConversationService, DestinationsService, ItineraryGenerationService
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnifiedReActAgent {
    
    private final AIService aiService;
    private final ToolRegistry toolRegistry;
    private final AgentMetricsService metricsService;
    
    private static final int MAX_ITERATIONS = 15;
    private static final Pattern TOOL_PATTERN = Pattern.compile("(?i)(?:use|call|execute)?\\s*(conversation|recommend_destinations|generate_itinerary|FINISH)", Pattern.CASE_INSENSITIVE);
    
    /**
     * 执行 ReAct 循环
     */
    public AgentResponse execute(Long userId, String sessionId, String message) {
        Timer.Sample sample = metricsService.startAgentExecution();
        
        try {
            AgentState state = AgentState.create(userId, sessionId, message);
            List<ReActStep> history = new ArrayList<>();
            
            log.info("🚀 UnifiedReActAgent starting for session: {}", sessionId);
            
            for (int i = 0; i < MAX_ITERATIONS; i++) {
                log.info("🔄 Iteration {}/{}", i + 1, MAX_ITERATIONS);
                
                // 1. Reasoning: Agent 思考下一步
                String thought = reason(state, history);
                log.info("💭 Thought: {}", thought);
                
                // 2. Acting: 选择并执行工具
                ActionResult actionResult = act(state, thought);
                log.info("⚡ Action: {} → {}", actionResult.getToolName(), 
                        actionResult.getSuccess() ? "Success" : "Failed");
                
                // 记录工具调用（复用阶段6的监控）
                if (actionResult.getDurationMs() != null) {
                    metricsService.recordToolCall(
                        actionResult.getToolName(), 
                        Duration.ofMillis(actionResult.getDurationMs())
                    );
                }
                
                // 3. Observation: 观察结果并更新状态
                state = observe(state, actionResult);
                log.info("👁️ Observation: {}", actionResult.getObservation());
                
                // 记录步骤
                history.add(ReActStep.builder()
                    .iteration(i + 1)
                    .thought(thought)
                    .action(actionResult.getToolName())
                    .observation(actionResult.getObservation())
                    .success(actionResult.getSuccess())
                    .build());
                
                // 4. 判断是否需要用户输入或已完成
                if (needsUserInput(state, actionResult)) {
                    metricsService.stopAgentExecution(sample, true);
                    return buildResponse(state, actionResult, history, "chat");
                }
                
                if (isComplete(state, actionResult)) {
                    log.info("✅ Agent completed task after {} iterations", i + 1);
                    metricsService.stopAgentExecution(sample, true);
                    return buildResponse(state, actionResult, history, determineActionType(actionResult));
                }
                
                // 5. 检查是否陷入循环
                if (isLooping(history)) {
                    log.warn("⚠️ Detected loop, breaking out");
                    metricsService.stopAgentExecution(sample, false);
                    return buildResponse(state, actionResult, history, "chat");
                }
            }
            
            log.warn("⚠️ Reached max iterations");
            metricsService.stopAgentExecution(sample, false);
            return buildFallbackResponse(state, history);
            
        } catch (Exception e) {
            log.error("❌ Agent execution failed", e);
            metricsService.stopAgentExecution(sample, false);
            throw new RuntimeException("Agent execution failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Reasoning: Agent 思考下一步
     */
    private String reason(AgentState state, List<ReActStep> history) {
        String prompt = buildReasoningPrompt(state, history);
        return aiService.chat(prompt);
    }
    
    /**
     * Acting: 执行工具
     */
    private ActionResult act(AgentState state, String thought) {
        String toolName = parseToolName(thought);
        
        if ("FINISH".equalsIgnoreCase(toolName)) {
            return ActionResult.builder()
                .toolName("FINISH")
                .success(true)
                .observation("Task completed")
                .build();
        }
        
        try {
            return toolRegistry.execute(toolName, state);
        } catch (Exception e) {
            log.error("Tool execution failed: {}", toolName, e);
            return ActionResult.builder()
                .toolName(toolName)
                .success(false)
                .observation("Error: " + e.getMessage())
                .error(e.getMessage())
                .build();
        }
    }
    
    /**
     * Observation: 更新状态
     */
    private AgentState observe(AgentState state, ActionResult result) {
        if (result.getResult() == null || !result.getSuccess()) {
            return state;
        }
        
        String toolName = result.getToolName();
        
        if ("conversation".equals(toolName)) {
            // ConversationTool 返回 ChatResponse
            if (result.getResult() instanceof com.travel.agent.dto.response.ChatResponse) {
                com.travel.agent.dto.response.ChatResponse response = 
                    (com.travel.agent.dto.response.ChatResponse) result.getResult();
                state.setIntent(response.getIntent());
            }
        } else if ("recommend_destinations".equals(toolName)) {
            // RecommendationTool 返回 List<AIDestinationRecommendation>
            @SuppressWarnings("unchecked")
            List<com.travel.agent.dto.AIDestinationRecommendation> recommendations = 
                (List<com.travel.agent.dto.AIDestinationRecommendation>) result.getResult();
            state.setRecommendations(recommendations);
        } else if ("generate_itinerary".equals(toolName)) {
            // ItineraryGenerationTool 返回 tripId
            state.setTripId((Long) result.getResult());
        }
        
        return state;
    }
    
    /**
     * 构建推理 Prompt
     */
    private String buildReasoningPrompt(AgentState state, List<ReActStep> history) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are a travel planning assistant. Analyze the current state and decide the next action.\n\n");
        
        // 当前状态
        prompt.append("Current State:\n");
        prompt.append("- User message: \"").append(state.getCurrentMessage()).append("\"\n");
        prompt.append("- Has intent analyzed: ").append(state.getIntent() != null).append("\n");
        
        if (state.getIntent() != null) {
            var intent = state.getIntent();
            prompt.append("  - Destination: ").append(intent.getDestination() != null ? intent.getDestination() : "unclear").append("\n");
            prompt.append("  - Days: ").append(intent.getDays() != null ? intent.getDays() : "unknown").append("\n");
            prompt.append("  - Budget: ").append(intent.getBudget() != null ? intent.getBudget() : "unknown").append("\n");
            prompt.append("  - Needs recommendation: ").append(intent.getNeedsRecommendation()).append("\n");
            prompt.append("  - Ready for itinerary: ").append(intent.getReadyForItinerary()).append("\n");
        }
        
        prompt.append("- Has recommendations: ").append(state.getRecommendations() != null && !state.getRecommendations().isEmpty()).append("\n");
        prompt.append("- Has selected destination: ").append(state.getSelectedDestination() != null).append("\n");
        prompt.append("- Has trip generated: ").append(state.getTripId() != null).append("\n\n");
        
        // 可用工具
        prompt.append("Available Tools:\n");
        prompt.append("1. conversation - Use when you need to chat with user to collect more information or provide responses\n");
        prompt.append("2. recommend_destinations - Use when user needs destination suggestions (intent.needsRecommendation == true)\n");
        prompt.append("3. generate_itinerary - Use when you have all required info (destination, days, budget) and intent.readyForItinerary == true\n");
        prompt.append("4. FINISH - Use when task is completed (e.g., trip generated or user just wants to chat)\n\n");
        
        // 对话轮次统计
        long conversationCount = history.stream()
            .filter(step -> "conversation".equals(step.getAction()))
            .count();
        prompt.append("- Conversation turns so far: ").append(conversationCount).append("\n\n");
        
        // 决策规则（更明确和果断）
        prompt.append("Decision Rules (IMPORTANT - Be decisive, don't chat endlessly):\n");
        prompt.append("1. If no intent analyzed yet → use 'conversation' (max 1 time)\n");
        prompt.append("2. If intent.needsRecommendation == true AND has basic info (interests/budget/days) → use 'recommend_destinations' IMMEDIATELY\n");
        prompt.append("3. If intent.readyForItinerary == true AND has destination → use 'generate_itinerary' IMMEDIATELY\n");
        prompt.append("4. If trip generated → use 'FINISH'\n");
        prompt.append("5. If conversation count >= 2 AND intent.needsRecommendation == true → MUST use 'recommend_destinations' (stop chatting!)\n");
        prompt.append("6. If user just chatting (no travel intent) → use 'conversation' then 'FINISH'\n\n");
        prompt.append("CRITICAL: After 2 conversation turns, you MUST take action (recommend or generate). Don't keep asking questions!\n\n");
        
        // 历史记录
        if (!history.isEmpty()) {
            prompt.append("Recent History:\n");
            int start = Math.max(0, history.size() - 3);
            for (int i = start; i < history.size(); i++) {
                ReActStep step = history.get(i);
                prompt.append(String.format("  %d. Action: %s → %s\n", 
                    step.getIteration(), step.getAction(), 
                    step.getObservation().length() > 100 ? step.getObservation().substring(0, 100) + "..." : step.getObservation()));
            }
            prompt.append("\n");
        }
        
        // 强制决策逻辑
        if (conversationCount >= 2 && state.getIntent() != null && 
            Boolean.TRUE.equals(state.getIntent().getNeedsRecommendation())) {
            prompt.append("\n⚠️ CRITICAL OVERRIDE: You have already had ").append(conversationCount)
                  .append(" conversation turns AND intent.needsRecommendation == true.\n");
            prompt.append("You MUST use 'recommend_destinations' NOW. Do NOT continue chatting!\n\n");
        }
        
        prompt.append("What should you do next? Respond with ONLY the tool name (conversation, recommend_destinations, generate_itinerary, or FINISH) and a brief reason.\n");
        prompt.append("Format: [TOOL_NAME] because [reason]");
        
        return prompt.toString();
    }
    
    /**
     * 解析工具名称
     */
    private String parseToolName(String thought) {
        if (thought == null || thought.trim().isEmpty()) {
            return "conversation";
        }
        
        Matcher matcher = TOOL_PATTERN.matcher(thought);
        if (matcher.find()) {
            String tool = matcher.group(1).toLowerCase();
            if ("finish".equalsIgnoreCase(tool)) {
                return "FINISH";
            }
            return tool;
        }
        
        // 默认使用对话工具
        return "conversation";
    }
    
    /**
     * 判断是否需要用户输入
     */
    private boolean needsUserInput(AgentState state, ActionResult result) {
        // 如果是对话工具且成功，需要等待用户回复
        return "conversation".equals(result.getToolName()) && result.getSuccess();
    }
    
    /**
     * 判断是否完成
     */
    private boolean isComplete(AgentState state, ActionResult result) {
        // FINISH 工具被调用
        if ("FINISH".equalsIgnoreCase(result.getToolName())) {
            return true;
        }
        
        // 行程已生成
        if (state.getTripId() != null) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 检查是否陷入循环
     */
    private boolean isLooping(List<ReActStep> history) {
        if (history.size() < 4) {
            return false;
        }
        
        // 检查最后4步是否重复相同的动作
        int size = history.size();
        String lastAction = history.get(size - 1).getAction();
        int sameActionCount = 0;
        
        for (int i = size - 1; i >= Math.max(0, size - 4); i--) {
            if (lastAction.equals(history.get(i).getAction())) {
                sameActionCount++;
            }
        }
        
        return sameActionCount >= 3;
    }
    
    /**
     * 构建响应
     */
    private AgentResponse buildResponse(AgentState state, ActionResult result, List<ReActStep> history, String actionType) {
        String message = result != null ? result.getObservation() : "Processing...";
        
        return AgentResponse.builder()
            .actionType(actionType)
            .message(message)
            .intent(state.getIntent())
            .recommendations(state.getRecommendations())
            .tripId(state.getTripId())
            .reasoningHistory(history)
            .metadata(state.getMetadata())
            .build();
    }
    
    /**
     * 确定动作类型
     */
    private String determineActionType(ActionResult result) {
        if (result == null) {
            return "chat";
        }
        
        String toolName = result.getToolName();
        if ("conversation".equals(toolName)) {
            return "chat";
        } else if ("recommend_destinations".equals(toolName)) {
            return "recommend";
        } else if ("generate_itinerary".equals(toolName)) {
            return "generate";
        } else if ("FINISH".equalsIgnoreCase(toolName)) {
            return "complete";
        }
        
        return "chat";
    }
    
    /**
     * 构建降级响应
     */
    private AgentResponse buildFallbackResponse(AgentState state, List<ReActStep> history) {
        return AgentResponse.builder()
            .actionType("chat")
            .message("抱歉，我遇到了一些问题。请重新描述您的需求。")
            .intent(state.getIntent())
            .reasoningHistory(history)
            .metadata(state.getMetadata())
            .build();
    }
}
