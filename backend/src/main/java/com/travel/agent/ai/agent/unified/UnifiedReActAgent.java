package com.travel.agent.ai.agent.unified;

import com.travel.agent.ai.agent.ActionResult;
import com.travel.agent.ai.agent.ReActStep;
import com.travel.agent.config.AgentConfig;
import com.travel.agent.dto.unified.StateConverter;
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
 * 统一 ReAct Agent 入口 —— 整个 AI 对话处理的核心总调度器。
 *
 * <p>架构设计要点：
 * <ol>
 *   <li><b>LLM 职责最小化</b>：LLM 仅负责结构化意图提取（StructuredIntentExtractor），
 *       避免让 LLM 做工具选择决策，降低不确定性与延迟。</li>
 *   <li><b>纯 Java 路由决策</b>：IntentRouter 根据提取的意图字段做确定性路由，
 *       逻辑清晰、可测试、可追踪。</li>
 *   <li><b>会话级状态持久化</b>：AgentStateStore 以 sessionId 为 key 将状态存入 Redis，
 *       支持多轮对话续接。</li>
 *   <li><b>全链路可观测</b>：Micrometer Observation 提供分布式追踪，
 *       AgentMetricsService 记录执行时长、工具调用次数等指标。</li>
 * </ol>
 *
 * <p>整体执行流程（单次请求）：
 * <pre>
 *   execute()
 *     └─ validateAndSanitizeInput()   // 输入校验 &amp; 清洗
 *         └─ executeInternal()
 *               ├─ stateStore.loadOrCreate()          // 加载/创建会话状态
 *               ├─ intentExtractor.extractAndMerge()  // LLM 提取意图
 *               ├─ intentRouter.route()               // 纯 Java 路由决策
 *               ├─ act()                              // 执行具体工具
 *               ├─ observe()                          // 更新 Agent 状态
 *               └─ buildResponse()                    // 组装返回对象
 * </pre>
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

    /**
     * 构造函数，由 Spring 容器自动注入所有依赖。
     *
     * <p>同时初始化一个 {@link ExecutorService}（无界缓存线程池），
     * 用于将 Agent 主逻辑异步执行，配合 {@link #execute} 中的超时控制。
     * 线程设为 daemon 线程，JVM 退出时不会阻塞关闭。
     */
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
        // 使用 CachedThreadPool + 自定义命名，方便在日志/线程 dump 中定位 Agent 线程
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r);
            thread.setName("agent-executor-" + thread.getId());
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Agent 对外暴露的唯一公共入口，处理单条用户消息。
     *
     * <p>执行策略：将核心逻辑提交到独立线程池异步执行，主线程阻塞等待，
     * 超时后取消任务并抛出异常，防止慢 LLM 调用拖垮调用方线程。
     *
     * @param userId    当前登录用户 ID
     * @param sessionId 会话标识（用于多轮对话状态续接）
     * @param message   用户输入的原始消息
     * @return {@link AgentResponse} 包含回复消息、意图、推荐列表、行程 ID 等
     * @throws RuntimeException 超时、中断或执行异常时抛出
     */
    public AgentResponse execute(Long userId, String sessionId, String message) {
        // 第一步：在主线程提前校验输入，避免把脏数据提交到线程池
        validateAndSanitizeInput(message);

        // 第二步：将核心执行逻辑提交到 Agent 专属线程池，实现异步执行
        CompletableFuture<AgentResponse> future = CompletableFuture.supplyAsync(
                () -> executeInternal(userId, sessionId, message),
                executorService
        );

        try {
            // 第三步：主线程阻塞等待，超时时间取自配置（executionTimeout）
            return future.get(
                    agentConfig.getExecutionTimeout().toSeconds(),
                    TimeUnit.SECONDS
            );
        } catch (TimeoutException e) {
            // 超时：记录日志 + 取消异步任务（发送中断信号），向上抛出
            log.error("❌ Agent execution timeout after {}s for session: {}",
                    agentConfig.getExecutionTimeout().toSeconds(), sessionId);
            future.cancel(true);
            throw new RuntimeException(
                    String.format("Agent execution timeout after %d seconds", agentConfig.getExecutionTimeout().toSeconds()),
                    e
            );
        } catch (InterruptedException e) {
            // 当前线程被中断（如服务关闭），恢复中断标志位后抛出
            Thread.currentThread().interrupt();
            throw new RuntimeException("Agent execution interrupted", e);
        } catch (ExecutionException e) {
            // 解包异步任务内部抛出的异常，保留原始 RuntimeException 类型
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException("Agent execution failed", cause);
        }
    }

    /**
     * 输入校验与安全清洗。
     *
     * <p>执行两项检查：
     * <ol>
     *   <li>长度校验：消息长度须在 [minLength, maxLength] 配置范围内。</li>
     *   <li>恶意内容检测（可选）：由配置项 {@code enableInputSanitization} 控制，
     *       检测到可疑内容时仅记录警告，不拦截（实际拦截逻辑可根据需求加强）。</li>
     * </ol>
     *
     * @param message 待校验的原始用户消息
     * @throws IllegalArgumentException 长度不符合要求时抛出
     */
    private void validateAndSanitizeInput(String message) {
        try {
            // 校验消息长度是否在合法范围内
            inputSanitizer.validateMessage(
                    message,
                    agentConfig.getMessageMinLength(),
                    agentConfig.getMessageMaxLength()
            );

            // 若启用输入清洗，额外检测 SQL 注入、XSS 等恶意内容
            if (agentConfig.isEnableInputSanitization() && inputSanitizer.containsMaliciousContent(message)) {
                log.warn("⚠️ Potentially malicious content detected in message");
            }
        } catch (IllegalArgumentException e) {
            log.error("❌ Input validation failed: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Agent 核心执行逻辑（运行在独立线程池线程中）。
     *
     * <p>按顺序执行以下步骤：
     * <ol>
     *   <li>输入清洗（可选 HTML/特殊字符转义）</li>
     *   <li>加载或创建会话状态（从 Redis 恢复多轮对话上下文）</li>
     *   <li>初始化 Trace（分布式追踪 span）</li>
     *   <li>LLM 意图提取并合并到状态</li>
     *   <li>IntentRouter 纯 Java 路由，得到决策（工具名 + 下一阶段）</li>
     *   <li>执行工具（act）</li>
     *   <li>观察结果并更新状态（observe）</li>
     *   <li>持久化状态到 Redis</li>
     *   <li>组装并返回 AgentResponse</li>
     * </ol>
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @param message   原始消息（已通过校验）
     * @return {@link AgentResponse}
     */
    private AgentResponse executeInternal(Long userId, String sessionId, String message) {
        // 启动 Micrometer 计时器，用于记录整次 Agent 执行耗时
        Timer.Sample sample = metricsService.startAgentExecution();
        boolean stopped = false;   // 标志位：防止 finally 中重复调用 stopAgentExecution
        UnifiedAgentState state = null;
        Observation trace = null;

        try {
            // 1. 若启用输入清洗，对消息进行 HTML 转义等处理，防止注入
            String sanitizedMessage = agentConfig.isEnableInputSanitization()
                    ? inputSanitizer.sanitizeInput(message)
                    : message;

            // 2. 从 Redis 加载已有会话状态，或为新会话创建初始状态对象
            state = stateStore.loadOrCreate(userId, sessionId, sanitizedMessage);

            // 3. 确保 state 中有 traceId（新会话自动生成，旧会话复用）
            String traceId = traceService.ensureTraceId(state);
            // 开启顶层 Observation span，后续工具调用会在此 span 下嵌套子 span
            trace = traceService.startExecutionTrace(sessionId, traceId);

            try (Observation.Scope ignored = trace.openScope()) {
                // 4. 日志脱敏：若启用日志掩码，对敏感词遮蔽后再打印
                String logMessage = agentConfig.isEnableLogMasking()
                        ? inputSanitizer.maskForLog(sanitizedMessage)
                        : sanitizedMessage;
                log.info("🚀 UnifiedReActAgent starting for session: {}, message: {}",
                        sessionId, inputSanitizer.truncate(logMessage, 100));

                // 5. 调用 LLM 提取结构化意图，并与已有意图合并（多轮累积）
                //    例如：第一轮只说了"我想去日本"，第二轮才说"5天"，合并后意图更完整
                state.incrementIteration();
                long intentStart = System.currentTimeMillis();
                state.setIntent(intentExtractor.extractAndMerge(sanitizedMessage, state.getIntent()));
                state.setIntentAnalyzed(true);
                state.setCurrentPhase(UnifiedAgentState.ExecutionPhase.ANALYZING_INTENT);
                log.info("⏱️ Intent extraction done in {}ms for session {}",
                        System.currentTimeMillis() - intentStart,
                        sessionId);

                // 6. IntentRouter 根据意图字段做确定性路由，返回：工具名 + 下一阶段 + 路由原因
                IntentRouter.Decision decision = intentRouter.route(state);
                // 记录阶段转换指标（用于 Grafana 监控状态流转频率）
                metricsService.recordStateTransition(
                        state.getCurrentPhase().name(),
                        decision.nextPhase().name()
                );
                // 将路由决策元数据写入 state，便于调试和日志追踪
                state.getMetadata().put("routedTool", decision.toolName());
                state.getMetadata().put("routeReason", decision.reason());
                state.setCurrentPhase(decision.nextPhase());

                // 7. 执行路由决定的工具（conversation / recommend / generate / FINISH）
                long actStart = System.currentTimeMillis();
                ActionResult actionResult = act(state, decision, traceId);
                log.info("⏱️ Tool execution ({}) done in {}ms for session {}",
                        actionResult != null ? actionResult.getToolName() : "unknown",
                        System.currentTimeMillis() - actStart,
                        sessionId);
                // 记录工具调用耗时指标
                if (actionResult.getDurationMs() != null) {
                    metricsService.recordToolCall(
                            actionResult.getToolName(),
                            Duration.ofMillis(actionResult.getDurationMs())
                    );
                }

                // 8. 根据工具执行结果更新 AgentState（阶段、推荐列表、tripId 等）
                state = observe(state, actionResult);
                // 9. 将最新 state 持久化回 Redis，供下一轮对话恢复使用
                stateStore.save(state);

                // 10. 构建本次执行的推理历史记录（供前端展示 Agent 思考过程）
                List<ReActStep> history = new ArrayList<>();
                history.add(ReActStep.builder()
                        .iteration(1)
                        .thought("Deterministic routing: " + decision.reason())
                        .action(actionResult.getToolName())
                        .observation(actionResult.getObservation())
                        .success(actionResult.getSuccess())
                        .build());

                // 11. 停止计时器并上报成功/失败标志
                boolean success = actionResult.getSuccess() != null && actionResult.getSuccess();
                metricsService.stopAgentExecution(sample, success);
                stopped = true;
                // 12. 组装最终响应并返回
                return buildResponse(state, actionResult, history, determineActionType(actionResult));
            }
        } catch (Exception e) {
            log.error("❌ Agent execution failed", e);
            // 将异常信息写入 trace span，便于分布式追踪系统（如 Jaeger）展示错误
            if (trace != null) {
                trace.error(e);
            }
            // 更新 state 为 FAILED 状态并持久化，避免下一轮对话使用脏状态
            if (state != null) {
                state.setCurrentPhase(UnifiedAgentState.ExecutionPhase.FAILED);
                state.addError("Agent execution failed: " + e.getMessage());
                stateStore.save(state);
            }
            if (!stopped) {
                metricsService.stopAgentExecution(sample, false);
                stopped = true;
            }
            throw new RuntimeException("Agent execution failed: " + e.getMessage(), e);
        } finally {
            // 兜底：确保计时器一定被停止（防止 metrics 泄漏）
            if (!stopped) {
                metricsService.stopAgentExecution(sample, false);
            }
            // 关闭顶层 trace span
            if (trace != null) {
                trace.stop();
            }
        }
    }

    /**
     * ReAct 中的 "Act" 阶段：根据路由决策执行对应工具。
     *
     * <p>特殊处理：若工具名为 {@code FINISH}，直接构造成功结果返回，无需调用工具注册表。
     * 其他工具通过 {@link ToolRegistry} 统一分发执行，并用子 Observation span 包裹，
     * 实现工具级别的分布式追踪。
     *
     * @param state    当前 Agent 状态（含意图、会话历史等）
     * @param decision IntentRouter 的路由决策（含工具名）
     * @param traceId  当前请求的追踪 ID
     * @return {@link ActionResult} 工具执行结果（包含 observation、result 对象、耗时等）
     */
    private ActionResult act(UnifiedAgentState state, IntentRouter.Decision decision, String traceId) {
        String toolName = decision.toolName();

        // FINISH 是虚拟工具：表示任务已完成，无需真正调用任何工具
        if (IntentRouter.TOOL_FINISH.equalsIgnoreCase(toolName)) {
            return ActionResult.builder()
                    .toolName(IntentRouter.TOOL_FINISH)
                    .success(true)
                    .observation("Task completed")
                    .build();
        }

        // 为本次工具调用开启子 span（嵌套在父 execution span 下）
        Observation toolTrace = traceService.startToolTrace(state.getSessionId(), traceId, toolName);
        try (Observation.Scope ignored = toolTrace.openScope()) {
            // 通过工具注册表按名称分发，实际执行 ConversationTool / RecommendTool / GenerateTool
            return toolRegistry.execute(toolName, state);
        } catch (Exception e) {
            log.error("Tool execution failed: {}", toolName, e);
            // 工具异常时返回失败结果，不向上抛出（由 observe 方法将 state 置为 FAILED）
            return ActionResult.builder()
                    .toolName(toolName)
                    .success(false)
                    .observation("Error: " + e.getMessage())
                    .error(e.getMessage())
                    .build();
        } finally {
            // 无论成功失败，都停止子 span
            toolTrace.stop();
        }
    }

    /**
     * ReAct 中的 "Observe" 阶段：根据工具执行结果更新 AgentState。
     *
     * <p>不同工具的状态更新逻辑：
     * <ul>
     *   <li>{@code conversation}：增加对话轮次计数，追加对话历史，进入 COLLECTING_INFO 阶段</li>
     *   <li>{@code recommend_destinations}：将推荐列表写入 state，进入 RECOMMENDATION_READY 阶段</li>
     *   <li>{@code generate_itinerary}：将生成的 tripId 写入 state，进入 ITINERARY_STARTED 阶段</li>
     *   <li>{@code FINISH}：进入 COMPLETED 阶段</li>
     *   <li>执行失败：进入 FAILED 阶段</li>
     * </ul>
     *
     * @param state  当前 Agent 状态
     * @param result 工具执行结果
     * @return 更新后的 {@link AgentState}
     */
    private UnifiedAgentState observe(UnifiedAgentState state, ActionResult result) {
        state.setMetadata("lastAction", result.getToolName());

        // 工具执行失败时，直接将 state 置为 FAILED，终止后续处理
        if (!Boolean.TRUE.equals(result.getSuccess())) {
            state.setCurrentPhase(UnifiedAgentState.ExecutionPhase.FAILED);
            state.addError(result.getError() != null ? result.getError() : "Tool execution failed");
            return state;
        }

        String toolName = result.getToolName();
        if (IntentRouter.TOOL_CONVERSATION.equals(toolName)) {
            appendConversationHistory(state, result);
            state.setCurrentPhase(UnifiedAgentState.ExecutionPhase.CONVERSING);
        } else if (IntentRouter.TOOL_RECOMMEND.equals(toolName)) {
            // 推荐工具：将 List<AIDestinationRecommendation> 写入 state
            if (result.getResult() instanceof List<?> recommendations) {
                @SuppressWarnings("unchecked")
                List<com.travel.agent.dto.AIDestinationRecommendation> typed =
                        (List<com.travel.agent.dto.AIDestinationRecommendation>) recommendations;
                state.setRecommendations(typed);
            }
            state.setCurrentPhase(UnifiedAgentState.ExecutionPhase.AWAITING_SELECTION);
        } else if (IntentRouter.TOOL_GENERATE.equals(toolName)) {
            // 行程生成工具：将异步创建的 tripId 写入 state，前端可据此轮询进度
            if (result.getResult() instanceof Long tripId) {
                state.setTripId(tripId);
            }
            state.setCurrentPhase(UnifiedAgentState.ExecutionPhase.GENERATING_ITINERARY);
            state.setItineraryStatus(UnifiedAgentState.ItineraryStatus.GENERATING);
        } else if (IntentRouter.TOOL_FINISH.equalsIgnoreCase(toolName)) {
            state.setCurrentPhase(UnifiedAgentState.ExecutionPhase.COMPLETED);
            if (state.getTripId() != null) {
                state.setItineraryStatus(UnifiedAgentState.ItineraryStatus.COMPLETED);
            }
        }

        return state;
    }

    /**
     * 将本轮用户消息和 AI 回复追加到会话历史，并按配置限制历史条数（滑动窗口）。
     *
     * <p>历史格式为字符串列表，交替存储 "user: ..." 和 "assistant: ..." 条目，
     * 后续 LLM 调用可将其拼接为上下文 prompt。
     *
     * <p>历史条数上限 = max(4, conversationHistoryLimit)，保证至少保留 2 轮对话。
     * 超出上限时从头部截断（保留最近的对话）。
     *
     * @param state  当前 Agent 状态（会话历史存储于此）
     * @param result 本轮对话工具的执行结果（含 AI 回复内容）
     */
    private void appendConversationHistory(UnifiedAgentState state, ActionResult result) {
        state.addConversationMessage("user", state.getCurrentMessage());

        if (result.getResult() instanceof com.travel.agent.dto.response.ChatResponse chatResponse) {
            state.addConversationMessage("assistant", chatResponse.getMessage());
        } else if (result.getObservation() != null) {
            state.addConversationMessage("assistant", result.getObservation());
        }
    }

    /**
     * 将 AgentState 和工具执行结果组装为前端可用的 {@link AgentResponse}。
     *
     * <p>消息内容优先级：
     * <ol>
     *   <li>若 result 是 {@link com.travel.agent.dto.response.ChatResponse}，使用其 message 字段</li>
     *   <li>否则使用 result.observation（工具执行的文字描述）</li>
     *   <li>兜底使用 "Processing..."</li>
     * </ol>
     *
     * @param state      最终 Agent 状态（含意图、推荐列表、tripId 等）
     * @param result     工具执行结果
     * @param history    本次执行的推理步骤历史（供前端展示思考过程）
     * @param actionType 动作类型字符串（"chat" / "recommend" / "generate" / "complete"）
     * @return 组装完成的 {@link AgentResponse}
     */
    private AgentResponse buildResponse(
            UnifiedAgentState state,
            ActionResult result,
            List<ReActStep> history,
            String actionType
    ) {
        // 确定最终返回给用户的文本消息
        String message = result != null ? result.getObservation() : "Processing...";
        if (result != null && result.getResult() instanceof com.travel.agent.dto.response.ChatResponse chatResponse) {
            // ChatResponse 包含更完整的 AI 回复，优先使用
            message = chatResponse.getMessage();
        }

        return AgentResponse.builder()
                .actionType(actionType)
                .message(message)
                .intent(StateConverter.toTravelIntent(state.getIntent())) // 兼容前端旧结构
                .recommendations(state.getRecommendations())     // 目的地推荐列表（可为 null）
                .tripId(state.getTripId())                       // 已创建的行程 ID（可为 null）
                .reasoningHistory(history)                       // Agent 推理步骤历史
                .metadata(state.getMetadata())                   // 路由原因等调试元数据
                .build();
    }

    /**
     * 根据工具执行结果的工具名映射为前端可识别的动作类型字符串。
     *
     * <p>映射关系：
     * <ul>
     *   <li>{@code conversation} → {@code "chat"}</li>
     *   <li>{@code recommend_destinations} → {@code "recommend"}</li>
     *   <li>{@code generate_itinerary} → {@code "generate"}</li>
     *   <li>{@code FINISH} → {@code "complete"}</li>
     *   <li>其他 / null → {@code "chat"}（默认兜底）</li>
     * </ul>
     *
     * @param result 工具执行结果（可为 null）
     * @return 动作类型字符串
     */
    private String determineActionType(ActionResult result) {
        if (result == null) {
            return "chat";
        }

        // Failed tool calls must not propagate their action type to the frontend —
        // a failed "generate" would leave the frontend with actionType=generate but tripId=null.
        if (!Boolean.TRUE.equals(result.getSuccess())) {
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
