package com.travel.agent.ai.state;

import lombok.Getter;
import org.bsc.langgraph4j.state.AgentState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 推荐系统 Agent 状态
 * 
 * 设计原则：
 * 1. 继承 LangGraph4j 的 AgentState
 * 2. 使用 Map 存储状态数据（符合 LangGraph4j 标准）
 * 3. 提供类型安全的访问方法
 * 4. 支持序列化（用于状态持久化）
 */
@Getter
public class RecommendationState extends AgentState {
    
    /**
     * 构造函数
     */
    public RecommendationState(Map<String, Object> initData) {
        super(initData);
    }
    
    // ==================== 输入参数 ====================
    
    public Long getUserId() {
        return (Long) data().get("userId");
    }
    
    public String getSessionId() {
        return (String) data().get("sessionId");
    }
    
    public String getDestinationPreference() {
        return (String) data().get("destinationPreference");
    }
    
    @SuppressWarnings("unchecked")
    public List<String> getInterests() {
        return (List<String>) data().getOrDefault("interests", new ArrayList<>());
    }
    
    public String getMood() {
        return (String) data().get("mood");
    }
    
    public Integer getBudgetLevel() {
        return (Integer) data().getOrDefault("budgetLevel", 2);
    }
    
    public Integer getDays() {
        return (Integer) data().getOrDefault("days", 5);
    }
    
    @SuppressWarnings("unchecked")
    public List<String> getExcludeNames() {
        return (List<String>) data().getOrDefault("excludeNames", new ArrayList<>());
    }
    
    // ==================== 中间状态 ====================
    
    /**
     * 意图分析结果
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getAnalyzedIntent() {
        return (Map<String, Object>) data().getOrDefault("analyzedIntent", new HashMap<>());
    }
    
    /**
     * RAG 搜索的候选目的地
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getCandidates() {
        return (List<Map<String, Object>>) data().getOrDefault("candidates", new ArrayList<>());
    }
    
    /**
     * 过滤后的目的地
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getFilteredDestinations() {
        return (List<Map<String, Object>>) data().getOrDefault("filteredDestinations", new ArrayList<>());
    }
    
    /**
     * 排序后的目的地
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getRankedDestinations() {
        return (List<Map<String, Object>>) data().getOrDefault("rankedDestinations", new ArrayList<>());
    }
    
    // ==================== 输出结果 ====================
    
    /**
     * 最终推荐结果（Top 3）
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getRecommendations() {
        return (List<Map<String, Object>>) data().getOrDefault("recommendations", new ArrayList<>());
    }
    
    // ==================== 执行状态 ====================
    
    public String getCurrentStep() {
        return (String) data().get("currentStep");
    }
    
    public Integer getProgress() {
        return (Integer) data().getOrDefault("progress", 0);
    }
    
    public String getProgressMessage() {
        return (String) data().get("progressMessage");
    }
    
    @SuppressWarnings("unchecked")
    public List<String> getErrors() {
        return (List<String>) data().getOrDefault("errors", new ArrayList<>());
    }
    
    public Boolean getCompleted() {
        return (Boolean) data().getOrDefault("completed", false);
    }
    
    // ==================== 元数据 ====================
    
    @SuppressWarnings("unchecked")
    public Map<String, Object> getMetadata() {
        return (Map<String, Object>) data().getOrDefault("metadata", new HashMap<>());
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 添加错误信息
     */
    public void addError(String error) {
        List<String> errors = getErrors();
        errors.add(error);
        data().put("errors", errors);
    }
    
    /**
     * 更新进度
     */
    public void updateProgress(int progress, String message) {
        data().put("progress", progress);
        data().put("progressMessage", message);
    }
    
    /**
     * 设置当前步骤
     */
    public void setCurrentStep(String step) {
        data().put("currentStep", step);
    }
}
