package com.travel.agent.ai.agent.unified;

import com.travel.agent.ai.agent.ActionResult;
import com.travel.agent.ai.agent.unified.tools.ConversationTool;
import com.travel.agent.ai.agent.unified.tools.ItineraryGenerationTool;
import com.travel.agent.ai.agent.unified.tools.RecommendationTool;
import com.travel.agent.config.AgentConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

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
    private final AgentConfig agentConfig;

    private final Map<String, UnifiedAgentTool> tools = new HashMap<>();
    private final ExecutorService executorService;

    /**
     * 构造函数
     */
    public ToolRegistry(
            ConversationTool conversationTool,
            RecommendationTool recommendationTool,
            ItineraryGenerationTool itineraryGenerationTool,
            AgentConfig agentConfig) {
        this.conversationTool = conversationTool;
        this.recommendationTool = recommendationTool;
        this.itineraryGenerationTool = itineraryGenerationTool;
        this.agentConfig = agentConfig;
        // 创建专用线程池
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r);
            thread.setName("tool-executor-" + thread.getId());
            thread.setDaemon(true);
            return thread;
        });
    }
    
    @PostConstruct
    public void init() {
        // 注册工具
        tools.put("conversation", conversationTool);
        tools.put("recommend_destinations", recommendationTool);
        tools.put("generate_itinerary", itineraryGenerationTool);
        
        log.info("✅ ToolRegistry initialized with {} tools", tools.size());
    }
    
    /**
     * 执行工具（带超时控制）
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

        // 使用 CompletableFuture 实现超时控制
        CompletableFuture<ActionResult> future = CompletableFuture.supplyAsync(
                () -> tool.execute(state),
                executorService
        );

        try {
            ActionResult result = future.get(
                    agentConfig.getToolExecutionTimeout().toSeconds(),
                    TimeUnit.SECONDS
            );
            result.setDurationMs(System.currentTimeMillis() - startTime);
            return result;

        } catch (TimeoutException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("⏱️ Tool execution timeout: {} after {}s", toolName, duration / 1000);
            future.cancel(true);  // 取消执行

            return ActionResult.builder()
                    .toolName(toolName)
                    .success(false)
                    .observation(String.format("Tool execution timeout after %d seconds",
                            agentConfig.getToolExecutionTimeout().toSeconds()))
                    .error("Timeout")
                    .durationMs(duration)
                    .build();

        } catch (InterruptedException e) {
            long duration = System.currentTimeMillis() - startTime;
            Thread.currentThread().interrupt();
            log.error("Tool execution interrupted: {}", toolName, e);

            return ActionResult.builder()
                    .toolName(toolName)
                    .success(false)
                    .observation("Tool execution interrupted")
                    .error("Interrupted")
                    .durationMs(duration)
                    .build();

        } catch (ExecutionException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Tool execution failed: {}", toolName, e.getCause());

            return ActionResult.builder()
                    .toolName(toolName)
                    .success(false)
                    .observation("Error: " + e.getCause().getMessage())
                    .error(e.getCause().getMessage())
                    .durationMs(duration)
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
