package com.travel.agent.monitoring;

import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent 指标服务
 * 使用 Micrometer 收集自定义业务指标
 */
@Slf4j
@Service
public class AgentMetricsService {
    
    private final MeterRegistry registry;
    
    // ==================== Agent 执行指标 ====================
    private final Counter agentExecutionTotal;
    private final Counter agentExecutionSuccess;
    private final Counter agentExecutionFailure;
    private final Timer agentExecutionTimer;
    private final AtomicInteger agentExecutionConcurrent;
    
    // ==================== LLM 调用指标 ====================
    private final Counter llmCallTotal;
    private final Counter llmCallSuccess;
    private final Counter llmCallFailure;
    private final Timer llmCallTimer;
    private final Counter llmTokensPrompt;
    private final Counter llmTokensCompletion;
    private final Counter llmTokensTotal;
    
    // ==================== 工具调用指标 ====================
    private final Counter toolCallTotal;
    
    // ==================== RAG 检索指标 ====================
    private final Counter ragSearchTotal;
    private final Timer ragSearchTimer;
    private final DistributionSummary ragSimilarityScore;
    
    // ==================== 知识库指标 ====================
    private final AtomicInteger knowledgeBaseDocuments;
    private final AtomicInteger knowledgeBaseSegments;
    private final Counter knowledgeBaseImports;
    
    // ==================== 状态机指标 ====================
    private final Counter stateTransitionTotal;
    private final Counter reflectionLoopTotal;
    
    public AgentMetricsService(MeterRegistry registry) {
        this.registry = registry;
        
        // 初始化 Agent 执行指标
        this.agentExecutionTotal = Counter.builder("agent.execution.total")
            .description("Total number of agent executions")
            .register(registry);
        
        this.agentExecutionSuccess = Counter.builder("agent.execution.success")
            .description("Number of successful agent executions")
            .register(registry);
        
        this.agentExecutionFailure = Counter.builder("agent.execution.failure")
            .description("Number of failed agent executions")
            .register(registry);
        
        this.agentExecutionTimer = Timer.builder("agent.execution.duration")
            .description("Agent execution duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(registry);
        
        this.agentExecutionConcurrent = registry.gauge("agent.execution.concurrent",
            new AtomicInteger(0));
        
        // 初始化 LLM 调用指标
        this.llmCallTotal = Counter.builder("llm.call.total")
            .description("Total number of LLM calls")
            .register(registry);
        
        this.llmCallSuccess = Counter.builder("llm.call.success")
            .description("Number of successful LLM calls")
            .register(registry);
        
        this.llmCallFailure = Counter.builder("llm.call.failure")
            .description("Number of failed LLM calls")
            .register(registry);
        
        this.llmCallTimer = Timer.builder("llm.call.duration")
            .description("LLM call duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(registry);
        
        this.llmTokensPrompt = Counter.builder("llm.tokens.prompt")
            .description("Total prompt tokens consumed")
            .register(registry);
        
        this.llmTokensCompletion = Counter.builder("llm.tokens.completion")
            .description("Total completion tokens consumed")
            .register(registry);
        
        this.llmTokensTotal = Counter.builder("llm.tokens.total")
            .description("Total tokens consumed")
            .register(registry);
        
        // 初始化工具调用指标
        this.toolCallTotal = Counter.builder("tool.call.total")
            .description("Total number of tool calls")
            .tag("tool", "unknown")
            .register(registry);
        
        // 初始化 RAG 检索指标
        this.ragSearchTotal = Counter.builder("rag.search.total")
            .description("Total number of RAG searches")
            .register(registry);
        
        this.ragSearchTimer = Timer.builder("rag.search.duration")
            .description("RAG search duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
        
        this.ragSimilarityScore = DistributionSummary.builder("rag.similarity.score")
            .description("RAG similarity scores")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
        
        // 初始化知识库指标
        this.knowledgeBaseDocuments = registry.gauge("knowledge.base.documents",
            new AtomicInteger(0));
        
        this.knowledgeBaseSegments = registry.gauge("knowledge.base.segments",
            new AtomicInteger(0));
        
        this.knowledgeBaseImports = Counter.builder("knowledge.base.imports")
            .description("Total number of knowledge base imports")
            .register(registry);
        
        // 初始化状态机指标
        this.stateTransitionTotal = Counter.builder("state.transition.total")
            .description("Total number of state transitions")
            .tag("from", "unknown")
            .tag("to", "unknown")
            .register(registry);
        
        this.reflectionLoopTotal = Counter.builder("reflection.loop.total")
            .description("Total number of reflection loops")
            .register(registry);
        
        log.info("✅ AgentMetricsService initialized with Micrometer");
    }
    
    // ==================== Agent 执行指标记录 ====================
    
    public void recordAgentExecution(Runnable execution) {
        agentExecutionTotal.increment();
        agentExecutionConcurrent.incrementAndGet();
        
        try {
            agentExecutionTimer.record(execution);
            agentExecutionSuccess.increment();
        } catch (Exception e) {
            agentExecutionFailure.increment();
            throw e;
        } finally {
            agentExecutionConcurrent.decrementAndGet();
        }
    }
    
    public Timer.Sample startAgentExecution() {
        agentExecutionTotal.increment();
        agentExecutionConcurrent.incrementAndGet();
        return Timer.start(registry);
    }
    
    public void stopAgentExecution(Timer.Sample sample, boolean success) {
        sample.stop(agentExecutionTimer);
        
        if (success) {
            agentExecutionSuccess.increment();
        } else {
            agentExecutionFailure.increment();
        }
        
        agentExecutionConcurrent.decrementAndGet();
    }
    
    // ==================== LLM 调用指标记录 ====================
    
    public void recordLLMCall(String model, int promptTokens, int completionTokens, 
                             Duration duration, boolean success) {
        llmCallTotal.increment();
        
        if (success) {
            llmCallSuccess.increment();
        } else {
            llmCallFailure.increment();
        }
        
        llmCallTimer.record(duration);
        
        llmTokensPrompt.increment(promptTokens);
        llmTokensCompletion.increment(completionTokens);
        llmTokensTotal.increment(promptTokens + completionTokens);
        
        log.debug("📊 LLM call recorded: model={}, tokens={}, duration={}ms", 
                model, promptTokens + completionTokens, duration.toMillis());
    }
    
    public Timer.Sample startLLMCall() {
        llmCallTotal.increment();
        return Timer.start(registry);
    }
    
    public void stopLLMCall(Timer.Sample sample, String model, int promptTokens, 
                           int completionTokens, boolean success) {
        sample.stop(Timer.builder("llm.call.duration")
            .tag("model", model)
            .register(registry));
        
        if (success) {
            llmCallSuccess.increment();
        } else {
            llmCallFailure.increment();
        }
        
        llmTokensPrompt.increment(promptTokens);
        llmTokensCompletion.increment(completionTokens);
        llmTokensTotal.increment(promptTokens + completionTokens);
    }
    
    // ==================== 工具调用指标记录 ====================
    
    public void recordToolCall(String toolName, Duration duration) {
        Counter.builder("tool.call.total")
            .tag("tool", toolName)
            .register(registry)
            .increment();
        
        Timer.builder("tool.call.duration")
            .tag("tool", toolName)
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry)
            .record(duration);
        
        log.debug("📊 Tool call recorded: tool={}, duration={}ms", 
                toolName, duration.toMillis());
    }
    
    public Timer.Sample startToolCall(String toolName) {
        return Timer.start(registry);
    }
    
    public void stopToolCall(Timer.Sample sample, String toolName) {
        sample.stop(Timer.builder("tool.call.duration")
            .tag("tool", toolName)
            .register(registry));
        
        Counter.builder("tool.call.total")
            .tag("tool", toolName)
            .register(registry)
            .increment();
    }
    
    // ==================== RAG 检索指标记录 ====================
    
    public void recordRAGSearch(Duration duration, double maxSimilarityScore) {
        ragSearchTotal.increment();
        ragSearchTimer.record(duration);
        ragSimilarityScore.record(maxSimilarityScore);
        
        log.debug("📊 RAG search recorded: duration={}ms, maxScore={}", 
                duration.toMillis(), maxSimilarityScore);
    }
    
    public Timer.Sample startRAGSearch() {
        return Timer.start(registry);
    }
    
    public void stopRAGSearch(Timer.Sample sample, double maxSimilarityScore) {
        sample.stop(ragSearchTimer);
        ragSearchTotal.increment();
        ragSimilarityScore.record(maxSimilarityScore);
    }
    
    // ==================== 知识库指标记录 ====================
    
    public void updateKnowledgeBaseStats(int documents, int segments) {
        knowledgeBaseDocuments.set(documents);
        knowledgeBaseSegments.set(segments);
    }
    
    public void recordKnowledgeBaseImport() {
        knowledgeBaseImports.increment();
    }
    
    // ==================== 状态机指标记录 ====================
    
    public void recordStateTransition(String fromState, String toState) {
        Counter.builder("state.transition.total")
            .tag("from", fromState)
            .tag("to", toState)
            .register(registry)
            .increment();
    }
    
    public void recordNodeExecution(String nodeName, Duration duration) {
        Timer.builder("node.execution.duration")
            .tag("node", nodeName)
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry)
            .record(duration);
    }
    
    public void recordReflectionLoop() {
        reflectionLoopTotal.increment();
    }
}
