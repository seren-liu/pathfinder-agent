package com.travel.agent.ai.agent.unified;

import com.travel.agent.ai.agent.ActionResult;
import com.travel.agent.ai.agent.ReActStep;
import com.travel.agent.config.AgentConfig;
import com.travel.agent.monitoring.AgentMetricsService;
import com.travel.agent.security.InputSanitizer;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * 统一 Agent 入口
 *
 * 新架构：
 * 1) LLM 仅做结构化意图提取
 * 2) IntentRouter 纯 Java 决策
 * 3) sessionId 维度持久化状态（Redis）
 * 4) Tracing + Metrics 全链路可观测
 */
@Slf4j
@Component
public class UnifiedReActAgent {

    private final ToolRegistry toolRegistry;
    private final AgentMetricsService metricsService;
    private final AgentConfig agentConfig;
    private final InputSanitizer inputSanitizer;
    private final AgentStateStore stateStore;
    private final StructuredIntentExtractor intentExtractor;
    private final IntentRouter intentRouter;
    private final AgentTraceService traceService;
    private final ExecutorService executorService;

    public UnifiedReActAgent(
            ToolRegistry toolRegistry,
            AgentMetricsService metricsService,
            AgentConfig agentConfig,
            InputSanitizer inputSanitizer,
            AgentStateStore stateStore,
            StructuredIntentExtractor intentExtractor,
            IntentRouter intentRouter,
            AgentTraceService traceService
    ) {
        this.toolRegistry = toolRegistry;
        this.metricsService = metricsService;
        this.agentConfig = agentConfig;
        this.inputSanitizer = inputSanitizer;
        this.stateStore = stateStore;
        this.intentExtractor = intentExtractor;
        this.intentRouter = intentRouter;
        this.traceService = traceService;
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r);
            thread.setName("agent-executor-" + thread.getId());
            thread.setDaemon(true);
            return thread;
        });
    }

    public AgentResponse execute(Long userId, String sessionId, String message) {
        validateAndSanitizeInput(message);

        CompletableFuture<AgentResponse> future = CompletableFuture.supplyAsync(
                () -> executeInternal(userId, sessionId, message),
                executorService
        );

        try {
            return future.get(
                    agentConfig.getExecutionTimeout().toSeconds(),
                    TimeUnit.SECONDS
            );
        } catch (TimeoutException e) {
            log.error("❌ Agent execution timeout after {}s for session: {}",
                    agentConfig.getExecutionTimeout().toSeconds(), sessionId);
            future.cancel(true);
            throw new RuntimeException(
                    String.format("Agent execution timeout after %d seconds", agentConfig.getExecutionTimeout().toSeconds()),
                    e
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Agent execution interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException("Agent execution failed", cause);
        }
    }

    private void validateAndSanitizeInput(String message) {
        try {
            inputSanitizer.validateMessage(
                    message,
                    agentConfig.getMessageMinLength(),
                    agentConfig.getMessageMaxLength()
            );

            if (agentConfig.isEnableInputSanitization() && inputSanitizer.containsMaliciousContent(message)) {
                log.warn("⚠️ Potentially malicious content detected in message");
            }
        } catch (IllegalArgumentException e) {
            log.error("❌ Input validation failed: {}", e.getMessage());
            throw e;
        }
    }

    private AgentResponse executeInternal(Long userId, String sessionId, String message) {
        Timer.Sample sample = metricsService.startAgentExecution();
        boolean stopped = false;
        AgentState state = null;
        Observation trace = null;

        try {
            String sanitizedMessage = agentConfig.isEnableInputSanitization()
                    ? inputSanitizer.sanitizeInput(message)
                    : message;

            state = stateStore.loadOrCreate(userId, sessionId, sanitizedMessage);
            String traceId = traceService.ensureTraceId(state);
            trace = traceService.startExecutionTrace(sessionId, traceId);

            try (Observation.Scope ignored = trace.openScope()) {
                String logMessage = agentConfig.isEnableLogMasking()
                        ? inputSanitizer.maskForLog(sanitizedMessage)
                        : sanitizedMessage;
                log.info("🚀 UnifiedReActAgent starting for session: {}, message: {}",
                        sessionId, inputSanitizer.truncate(logMessage, 100));

                state.setIntent(intentExtractor.extractAndMerge(sanitizedMessage, state.getIntent()));
                state.setPhase(AgentState.AgentPhase.INTENT_EXTRACTED);

                IntentRouter.Decision decision = intentRouter.route(state);
                metricsService.recordStateTransition(
                        state.getPhase().name(),
                        decision.nextPhase().name()
                );
                state.getMetadata().put("routedTool", decision.toolName());
                state.getMetadata().put("routeReason", decision.reason());
                state.setPhase(decision.nextPhase());

                ActionResult actionResult = act(state, decision, traceId);
                if (actionResult.getDurationMs() != null) {
                    metricsService.recordToolCall(
                            actionResult.getToolName(),
                            Duration.ofMillis(actionResult.getDurationMs())
                    );
                }

                state = observe(state, actionResult);
                stateStore.save(state);

                List<ReActStep> history = new ArrayList<>();
                history.add(ReActStep.builder()
                        .iteration(1)
                        .thought("Deterministic routing: " + decision.reason())
                        .action(actionResult.getToolName())
                        .observation(actionResult.getObservation())
                        .success(actionResult.getSuccess())
                        .build());

                boolean success = actionResult.getSuccess() != null && actionResult.getSuccess();
                metricsService.stopAgentExecution(sample, success);
                stopped = true;
                return buildResponse(state, actionResult, history, determineActionType(actionResult));
            }
        } catch (Exception e) {
            log.error("❌ Agent execution failed", e);
            if (trace != null) {
                trace.error(e);
            }
            if (state != null) {
                state.setPhase(AgentState.AgentPhase.FAILED);
                state.setLastAction("error");
                stateStore.save(state);
            }
            if (!stopped) {
                metricsService.stopAgentExecution(sample, false);
                stopped = true;
            }
            throw new RuntimeException("Agent execution failed: " + e.getMessage(), e);
        } finally {
            if (!stopped) {
                metricsService.stopAgentExecution(sample, false);
            }
            if (trace != null) {
                trace.stop();
            }
        }
    }

    private ActionResult act(AgentState state, IntentRouter.Decision decision, String traceId) {
        String toolName = decision.toolName();
        if (IntentRouter.TOOL_FINISH.equalsIgnoreCase(toolName)) {
            return ActionResult.builder()
                    .toolName(IntentRouter.TOOL_FINISH)
                    .success(true)
                    .observation("Task completed")
                    .build();
        }

        Observation toolTrace = traceService.startToolTrace(state.getSessionId(), traceId, toolName);
        try (Observation.Scope ignored = toolTrace.openScope()) {
            return toolRegistry.execute(toolName, state);
        } catch (Exception e) {
            log.error("Tool execution failed: {}", toolName, e);
            return ActionResult.builder()
                    .toolName(toolName)
                    .success(false)
                    .observation("Error: " + e.getMessage())
                    .error(e.getMessage())
                    .build();
        } finally {
            toolTrace.stop();
        }
    }

    private AgentState observe(AgentState state, ActionResult result) {
        state.setLastAction(result.getToolName());

        if (!Boolean.TRUE.equals(result.getSuccess())) {
            state.setPhase(AgentState.AgentPhase.FAILED);
            return state;
        }

        String toolName = result.getToolName();
        if (IntentRouter.TOOL_CONVERSATION.equals(toolName)) {
            if (state.getConversationTurns() == null) {
                state.setConversationTurns(0);
            }
            state.setConversationTurns(state.getConversationTurns() + 1);
            appendConversationHistory(state, result);
            state.setPhase(AgentState.AgentPhase.COLLECTING_INFO);
        } else if (IntentRouter.TOOL_RECOMMEND.equals(toolName)) {
            if (result.getResult() instanceof List<?> recommendations) {
                @SuppressWarnings("unchecked")
                List<com.travel.agent.dto.AIDestinationRecommendation> typed =
                        (List<com.travel.agent.dto.AIDestinationRecommendation>) recommendations;
                state.setRecommendations(typed);
            }
            state.setPhase(AgentState.AgentPhase.RECOMMENDATION_READY);
        } else if (IntentRouter.TOOL_GENERATE.equals(toolName)) {
            if (result.getResult() instanceof Long tripId) {
                state.setTripId(tripId);
            }
            state.setPhase(AgentState.AgentPhase.ITINERARY_STARTED);
        } else if (IntentRouter.TOOL_FINISH.equalsIgnoreCase(toolName)) {
            state.setPhase(AgentState.AgentPhase.COMPLETED);
        }

        return state;
    }

    private void appendConversationHistory(AgentState state, ActionResult result) {
        if (state.getConversationHistory() == null) {
            state.setConversationHistory(new ArrayList<>());
        }

        state.getConversationHistory().add("user: " + state.getCurrentMessage());

        if (result.getResult() instanceof com.travel.agent.dto.response.ChatResponse chatResponse) {
            state.getConversationHistory().add("assistant: " + chatResponse.getMessage());
        } else if (result.getObservation() != null) {
            state.getConversationHistory().add("assistant: " + result.getObservation());
        }

        int limit = Math.max(4, agentConfig.getConversationHistoryLimit());
        if (state.getConversationHistory().size() > limit) {
            state.setConversationHistory(new ArrayList<>(
                    state.getConversationHistory().subList(
                            state.getConversationHistory().size() - limit,
                            state.getConversationHistory().size()
                    )
            ));
        }
    }

    private AgentResponse buildResponse(
            AgentState state,
            ActionResult result,
            List<ReActStep> history,
            String actionType
    ) {
        String message = result != null ? result.getObservation() : "Processing...";
        if (result != null && result.getResult() instanceof com.travel.agent.dto.response.ChatResponse chatResponse) {
            message = chatResponse.getMessage();
        }

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

    private String determineActionType(ActionResult result) {
        if (result == null) {
            return "chat";
        }

        String toolName = result.getToolName();
        if (IntentRouter.TOOL_CONVERSATION.equals(toolName)) {
            return "chat";
        }
        if (IntentRouter.TOOL_RECOMMEND.equals(toolName)) {
            return "recommend";
        }
        if (IntentRouter.TOOL_GENERATE.equals(toolName)) {
            return "generate";
        }
        if (IntentRouter.TOOL_FINISH.equalsIgnoreCase(toolName)) {
            return "complete";
        }

        return "chat";
    }
}
