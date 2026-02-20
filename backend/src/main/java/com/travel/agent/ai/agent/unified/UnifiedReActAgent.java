package com.travel.agent.ai.agent.unified;

import com.travel.agent.ai.agent.ActionResult;
import com.travel.agent.ai.agent.ReActStep;
import com.travel.agent.config.AgentConfig;
import com.travel.agent.monitoring.AgentMetricsService;
import com.travel.agent.security.InputSanitizer;
import com.travel.agent.service.AIService;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 统一的 ReAct Agent 入口
 *
 * ReAct 循环：Reasoning → Acting → Observation → 判断是否继续
 *
 * 复用企业级基础设施：
 * - AgentMetricsService（阶段6）：监控指标
 * - ToolRegistry：工具编排
 * - 现有服务：ConversationService, DestinationsService, ItineraryGenerationService
 */
@Slf4j
@Component
public class UnifiedReActAgent {

    private final AIService aiService;
    private final ToolRegistry toolRegistry;
    private final AgentMetricsService metricsService;
    private final AgentConfig agentConfig;
    private final InputSanitizer inputSanitizer;
    private final ExecutorService executorService;

    private static final Pattern TOOL_PATTERN = Pattern.compile("(?i)(?:use|call|execute)?\\s*(conversation|recommend_destinations|generate_itinerary|FINISH)", Pattern.CASE_INSENSITIVE);

    /**
     * 构造函数注入（Spring 自动装配）
     */
    public UnifiedReActAgent(
            AIService aiService,
            ToolRegistry toolRegistry,
            AgentMetricsService metricsService,
            AgentConfig agentConfig,
            InputSanitizer inputSanitizer) {
        this.aiService = aiService;
        this.toolRegistry = toolRegistry;
        this.metricsService = metricsService;
        this.agentConfig = agentConfig;
        this.inputSanitizer = inputSanitizer;
        // 创建专用线程池用于超时控制
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r);
            thread.setName("agent-executor-" + thread.getId());
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 执行 ReAct 循环（带超时控制）
     */
    public AgentResponse execute(Long userId, String sessionId, String message) {
        // 1. 输入验证（第一道防线）
        validateAndSanitizeInput(message);

        // 2. 使用 CompletableFuture 实现超时控制
        CompletableFuture<AgentResponse> future = CompletableFuture.supplyAsync(
                () -> executeInternal(userId, sessionId, message),
                executorService
        );

        try {
            // 等待执行完成，设置总超时时间
            return future.get(
                    agentConfig.getExecutionTimeout().toSeconds(),
                    TimeUnit.SECONDS
            );
        } catch (TimeoutException e) {
            log.error("❌ Agent execution timeout after {}s for session: {}",
                    agentConfig.getExecutionTimeout().toSeconds(), sessionId);
            future.cancel(true);  // 取消执行
            throw new RuntimeException(
                    String.format("Agent execution timeout after %d seconds",
                            agentConfig.getExecutionTimeout().toSeconds()),
                    e
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Agent execution interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException("Agent execution failed", cause);
        }
    }

    /**
     * 验证并净化输入
     */
    private void validateAndSanitizeInput(String message) {
        try {
            // 1. 长度验证
            inputSanitizer.validateMessage(
                    message,
                    agentConfig.getMessageMinLength(),
                    agentConfig.getMessageMaxLength()
            );

            // 2. 恶意内容检测
            if (agentConfig.isEnableInputSanitization() &&
                    inputSanitizer.containsMaliciousContent(message)) {
                log.warn("⚠️ Potentially malicious content detected in message");
            }

        } catch (IllegalArgumentException e) {
            log.error("❌ Input validation failed: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 内部执行方法（实际的 ReAct 循环）
     */
    private AgentResponse executeInternal(Long userId, String sessionId, String message) {
        Timer.Sample sample = metricsService.startAgentExecution();

        try {
            // 净化用户输入
            String sanitizedMessage = agentConfig.isEnableInputSanitization()
                    ? inputSanitizer.sanitizeInput(message)
                    : message;

            AgentState state = AgentState.create(userId, sessionId, sanitizedMessage);
            List<ReActStep> history = new ArrayList<>();

            // 日志脱敏
            String logMessage = agentConfig.isEnableLogMasking()
                    ? inputSanitizer.maskForLog(sanitizedMessage)
                    : sanitizedMessage;
            log.info("🚀 UnifiedReActAgent starting for session: {}, message: {}",
                    sessionId, inputSanitizer.truncate(logMessage, 100));

            for (int i = 0; i < agentConfig.getMaxIterations(); i++) {
                log.info("🔄 Iteration {}/{}", i + 1, agentConfig.getMaxIterations());

                // 1. Reasoning: Agent 思考下一步（带超时控制）
                String thought = reasonWithTimeout(state, history);

                // 日志输出（根据配置决定是否截断）
                if (agentConfig.isEnableVerboseLogging()) {
                    log.info("💭 Thought: {}", thought);
                } else {
                    log.info("💭 Thought: {}", inputSanitizer.truncate(thought, agentConfig.getPromptLogTruncateLength()));
                }

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
     * Reasoning with timeout: 带超时控制的推理
     */
    private String reasonWithTimeout(AgentState state, List<ReActStep> history) {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(
                () -> reason(state, history),
                executorService
        );

        try {
            return future.get(
                    agentConfig.getLlmTimeout().toSeconds(),
                    TimeUnit.SECONDS
            );
        } catch (TimeoutException e) {
            log.error("❌ LLM reasoning timeout after {}s", agentConfig.getLlmTimeout().toSeconds());
            future.cancel(true);
            throw new RuntimeException(
                    String.format("LLM call timeout after %d seconds", agentConfig.getLlmTimeout().toSeconds()),
                    e
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("LLM call interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("LLM call failed", e.getCause());
        }
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
     *
     * 设计原则：
     * - 明确的决策规则，避免 LLM 犹豫不决
     * - 强制触发机制，2 轮对话后必须采取行动
     * - 只保留最近 3 步历史，避免 token 过多
     */
    private String buildReasoningPrompt(AgentState state, List<ReActStep> history) {
        StringBuilder prompt = new StringBuilder();

        // 1. 角色定义
        prompt.append("You are a travel planning assistant. Analyze the current state and decide the next action.\n\n");

        // 2. 当前状态
        prompt.append("Current State:\n");

        // ⚠️ 安全: 转义用户输入，防止 Prompt 注入
        String safeMessage = agentConfig.isEnablePromptInjectionProtection()
                ? inputSanitizer.escapeForPrompt(state.getCurrentMessage())
                : state.getCurrentMessage();
        prompt.append("- User message: \"").append(safeMessage).append("\"\n");
        prompt.append("- Has intent analyzed: ").append(state.getIntent() != null).append("\n");

        if (state.getIntent() != null) {
            var intent = state.getIntent();
            prompt.append("  - Destination: ").append(intent.getDestination() != null ? intent.getDestination() : "unclear").append("\n");
            prompt.append("  - Days: ").append(intent.getDays() != null ? intent.getDays() : "unknown").append("\n");
            prompt.append("  - Budget: ").append(intent.getBudget() != null ? intent.getBudget() : "unknown").append("\n");
            prompt.append("  - Interests: ").append(intent.getInterests() != null ? intent.getInterests() : "unknown").append("\n");
            prompt.append("  - needsRecommendation: ").append(intent.getNeedsRecommendation()).append("\n");
            prompt.append("  - readyForItinerary: ").append(intent.getReadyForItinerary()).append("\n");
        }

        prompt.append("- Has recommendations: ").append(state.getRecommendations() != null && !state.getRecommendations().isEmpty()).append("\n");
        prompt.append("- Has selected destination: ").append(state.getSelectedDestination() != null).append("\n");
        prompt.append("- Has trip generated: ").append(state.getTripId() != null).append("\n\n");

        // 3. 可用工具
        prompt.append("Available Tools:\n");
        prompt.append("1. conversation - Use when you need to chat with user to collect more information or provide responses\n");
        prompt.append("2. recommend_destinations - Use when user needs destination suggestions (intent.needsRecommendation == true)\n");
        prompt.append("3. generate_itinerary - Use when you have all required info (destination, days, budget) and intent.readyForItinerary == true\n");
        prompt.append("4. FINISH - Use when task is completed (e.g., trip generated or user just wants to chat)\n\n");

        // 4. 决策规则（关键！强规则，非 hints）
        prompt.append("Decision Rules (IMPORTANT - Be decisive, don't chat endlessly):\n");
        prompt.append("1. If no intent analyzed yet → use 'conversation' (max 1 time)\n");
        prompt.append("2. If intent.needsRecommendation == true AND has basic info (interests/budget/days) → use 'recommend_destinations' IMMEDIATELY\n");
        prompt.append("3. If intent.readyForItinerary == true AND has destination → use 'generate_itinerary' IMMEDIATELY\n");
        prompt.append("4. If trip generated → use 'FINISH'\n");
        prompt.append("5. If conversation count >= 2 AND intent.needsRecommendation == true → MUST use 'recommend_destinations' (stop chatting!)\n");
        prompt.append("6. If user just chatting (no travel intent) → use 'conversation' then 'FINISH'\n\n");
        prompt.append("CRITICAL: After 2 conversation turns, you MUST take action (recommend or generate). Don't keep asking questions!\n\n");

        // 5. 对话轮次统计
        long conversationCount = history.stream()
            .filter(step -> "conversation".equals(step.getAction()))
            .count();
        prompt.append("- Conversation turns so far: ").append(conversationCount).append("\n\n");

        // 6. 历史记录（最近 3 步）
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

        // 7. 强制决策逻辑（防止无限对话）
        if (conversationCount >= 2 && state.getIntent() != null &&
            Boolean.TRUE.equals(state.getIntent().getNeedsRecommendation())) {
            prompt.append("\n⚠️ CRITICAL OVERRIDE: You have already had ")
                  .append(conversationCount)
                  .append(" conversation turns AND intent.needsRecommendation == true.\n");
            prompt.append("You MUST use 'recommend_destinations' NOW. Do NOT continue chatting!\n\n");
        }

        if (conversationCount >= 2 && state.getIntent() != null &&
            Boolean.TRUE.equals(state.getIntent().getReadyForItinerary())) {
            prompt.append("\n⚠️ CRITICAL OVERRIDE: You have already had ")
                  .append(conversationCount)
                  .append(" conversation turns AND intent.readyForItinerary == true.\n");
            prompt.append("You MUST use 'generate_itinerary' NOW. Do NOT continue chatting!\n\n");
        }

        // 8. 输出格式（JSON 格式，便于解析）
        prompt.append("\n📋 Response Format:\n");
        prompt.append("Respond with JSON only:\n");
        prompt.append("{\n");
        prompt.append("  \"action\": \"conversation|recommend_destinations|generate_itinerary|FINISH\",\n");
        prompt.append("  \"reasoning\": \"Your step-by-step analysis...\"\n");
        prompt.append("}\n");

        return prompt.toString();
    }

    /**
     * 解析工具名称（支持 JSON 格式和文本格式）
     * 三层解析策略：JSON → 正则 → 默认
     */
    private String parseToolName(String thought) {
        if (thought == null || thought.trim().isEmpty()) {
            return "conversation";
        }

        // 1. 优先尝试解析 JSON 格式
        try {
            String jsonPart = extractJson(thought);
            if (jsonPart != null) {
                Pattern actionPattern = Pattern.compile("\"action\"\\s*:\\s*\"([^\"]+)\"");
                Matcher actionMatcher = actionPattern.matcher(jsonPart);
                if (actionMatcher.find()) {
                    String action = actionMatcher.group(1).trim();
                    log.info("📋 Parsed action from JSON: {}", action);

                    if (action.matches("(?i)(conversation|recommend_destinations|generate_itinerary|FINISH)")) {
                        return "FINISH".equalsIgnoreCase(action) ? "FINISH" : action.toLowerCase();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse JSON, falling back to pattern matching", e);
        }

        // 2. 回退到正则表达式匹配
        Matcher matcher = TOOL_PATTERN.matcher(thought);
        if (matcher.find()) {
            String tool = matcher.group(1).toLowerCase();
            if ("finish".equalsIgnoreCase(tool)) {
                return "FINISH";
            }
            return tool;
        }

        // 3. 默认使用对话工具
        log.warn("⚠️ Could not parse tool from thought, defaulting to conversation");
        return "conversation";
    }

    /**
     * 从文本中提取 JSON 部分
     */
    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');

        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }

        return null;
    }

    /**
     * 判断是否需要用户输入
     * conversation 工具成功 → 需要等待用户回复
     */
    private boolean needsUserInput(AgentState state, ActionResult result) {
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

        // 推荐结果已生成 → 返回 actionType: "recommend" 给前端
        if ("recommend_destinations".equals(result.getToolName()) && result.getSuccess()
                && state.getRecommendations() != null && !state.getRecommendations().isEmpty()) {
            return true;
        }

        // 行程已生成 → 返回 actionType: "generate" 给前端
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

        return sameActionCount >= agentConfig.getLoopDetectionThreshold();
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
