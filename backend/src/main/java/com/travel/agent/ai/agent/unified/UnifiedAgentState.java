package com.travel.agent.ai.agent.unified;

import com.travel.agent.dto.AIDestinationRecommendation;
import com.travel.agent.dto.unified.UnifiedTravelIntent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一 Agent 状态
 * 
 * 设计原则：
 * 1. Single Source of Truth - 使用 UnifiedTravelIntent 作为唯一意图模型
 * 2. Immutability - 使用 @Builder(toBuilder = true) 支持不可变更新
 * 3. Serializable - 支持 Redis 缓存和状态持久化
 * 4. Clear Separation - 区分会话状态、意图状态、执行状态
 * 
 * 替代：
 * - AgentState (旧版)
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedAgentState implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    // ========== 会话状态 ==========
    
    /** 用户ID */
    private Long userId;
    
    /** 会话ID */
    private String sessionId;
    
    /** 当前用户消息 */
    private String currentMessage;
    
    /** 对话历史 (最近N条) */
    @Builder.Default
    private List<ConversationMessage> conversationHistory = new ArrayList<>();
    
    /** 会话创建时间 */
    private LocalDateTime sessionCreatedAt;
    
    /** 最后更新时间 */
    private LocalDateTime lastUpdatedAt;
    
    // ========== 意图状态 ==========
    
    /** 统一旅行意图 (核心状态) */
    private UnifiedTravelIntent intent;
    
    /** 意图是否已分析 */
    @Builder.Default
    private Boolean intentAnalyzed = false;
    
    // ========== 推荐状态 ==========
    
    /** 推荐的目的地列表 */
    @Builder.Default
    private List<AIDestinationRecommendation> recommendations = new ArrayList<>();
    
    /** 已排除的目的地名称 (用于"换一批") */
    @Builder.Default
    private List<String> excludedDestinations = new ArrayList<>();
    
    /** 用户选择的目的地 */
    private AIDestinationRecommendation selectedDestination;
    
    // ========== 行程状态 ==========
    
    /** 生成的行程ID */
    private Long tripId;
    
    /** 行程生成状态 */
    @Builder.Default
    private ItineraryStatus itineraryStatus = ItineraryStatus.NOT_STARTED;
    
    // ========== 执行状态 ==========
    
    /** 当前执行阶段 */
    @Builder.Default
    private ExecutionPhase currentPhase = ExecutionPhase.INITIAL;
    
    /** ReAct 循环次数 */
    @Builder.Default
    private Integer iterationCount = 0;
    
    /** 最大循环次数 */
    @Builder.Default
    private Integer maxIterations = 10;
    
    /** 是否应该终止 */
    @Builder.Default
    private Boolean shouldTerminate = false;
    
    /** 终止原因 */
    private String terminationReason;
    
    // ========== 元数据 ==========
    
    /** 扩展元数据 */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
    
    /** 错误信息 */
    @Builder.Default
    private List<String> errors = new ArrayList<>();
    
    // ========== 枚举定义 ==========
    
    /**
     * 执行阶段
     */
    public enum ExecutionPhase {
        /** 初始阶段 */
        INITIAL,
        /** 意图分析中 */
        ANALYZING_INTENT,
        /** 对话收集信息中 */
        CONVERSING,
        /** 推荐目的地中 */
        RECOMMENDING,
        /** 等待用户选择 */
        AWAITING_SELECTION,
        /** 生成行程中 */
        GENERATING_ITINERARY,
        /** 已完成 */
        COMPLETED,
        /** 失败 */
        FAILED
    }
    
    /**
     * 行程生成状态
     */
    public enum ItineraryStatus {
        /** 未开始 */
        NOT_STARTED,
        /** 规划中 */
        PLANNING,
        /** 生成中 */
        GENERATING,
        /** 已完成 */
        COMPLETED,
        /** 失败 */
        FAILED
    }
    
    /**
     * 对话消息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversationMessage implements Serializable {
        private static final long serialVersionUID = 1L;
        
        /** 角色 (user/assistant) */
        private String role;
        
        /** 消息内容 */
        private String content;
        
        /** 时间戳 */
        private LocalDateTime timestamp;
    }
    
    // ========== 辅助方法 ==========
    
    /**
     * 创建初始状态
     */
    public static UnifiedAgentState create(Long userId, String sessionId, String message) {
        LocalDateTime now = LocalDateTime.now();
        
        return UnifiedAgentState.builder()
            .userId(userId)
            .sessionId(sessionId)
            .currentMessage(message)
            .sessionCreatedAt(now)
            .lastUpdatedAt(now)
            .intent(UnifiedTravelIntent.createDefault(userId, sessionId))
            .intentAnalyzed(false)
            .currentPhase(ExecutionPhase.INITIAL)
            .iterationCount(0)
            .maxIterations(10)
            .shouldTerminate(false)
            .itineraryStatus(ItineraryStatus.NOT_STARTED)
            .conversationHistory(new ArrayList<>())
            .recommendations(new ArrayList<>())
            .excludedDestinations(new ArrayList<>())
            .metadata(new HashMap<>())
            .errors(new ArrayList<>())
            .build();
    }
    
    /**
     * 添加对话消息
     */
    public void addConversationMessage(String role, String content) {
        if (conversationHistory == null) {
            conversationHistory = new ArrayList<>();
        }
        
        conversationHistory.add(ConversationMessage.builder()
            .role(role)
            .content(content)
            .timestamp(LocalDateTime.now())
            .build());
        
        // 保留最近20条消息
        if (conversationHistory.size() > 20) {
            conversationHistory = new ArrayList<>(
                conversationHistory.subList(conversationHistory.size() - 20, conversationHistory.size())
            );
        }
        
        this.lastUpdatedAt = LocalDateTime.now();
    }
    
    /**
     * 添加错误
     */
    public void addError(String error) {
        if (errors == null) {
            errors = new ArrayList<>();
        }
        errors.add(error);
        this.lastUpdatedAt = LocalDateTime.now();
    }
    
    /**
     * 增加迭代次数
     */
    public void incrementIteration() {
        this.iterationCount++;
        this.lastUpdatedAt = LocalDateTime.now();
        
        // 检查是否超过最大迭代次数
        if (this.iterationCount >= this.maxIterations) {
            this.shouldTerminate = true;
            this.terminationReason = "Max iterations reached";
        }
    }
    
    /**
     * 判断是否应该推荐
     */
    public boolean shouldRecommend() {
        return intent != null 
            && Boolean.TRUE.equals(intent.getNeedsRecommendation())
            && (recommendations == null || recommendations.isEmpty());
    }
    
    /**
     * 判断是否应该生成行程
     */
    public boolean shouldGenerateItinerary() {
        return intent != null 
            && Boolean.TRUE.equals(intent.getReadyForItinerary())
            && intent.hasEnoughInfoForItinerary()
            && tripId == null;
    }
    
    /**
     * 判断是否需要更多对话
     */
    public boolean needsMoreConversation() {
        return intent != null 
            && intent.needsMoreInfo()
            && !Boolean.TRUE.equals(intent.getNeedsRecommendation())
            && !Boolean.TRUE.equals(intent.getReadyForItinerary());
    }
    
    /**
     * 设置元数据
     */
    public void setMetadata(String key, Object value) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        metadata.put(key, value);
        this.lastUpdatedAt = LocalDateTime.now();
    }
    
    /**
     * 获取元数据
     */
    public Object getMetadata(String key) {
        return metadata != null ? metadata.get(key) : null;
    }
}
