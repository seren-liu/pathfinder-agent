package com.travel.agent.ai.agent.unified;

import com.travel.agent.ai.agent.ActionResult;

/**
 * 统一 Agent 工具接口
 * 用于 UnifiedReActAgent 的工具，包装现有服务
 * 
 * 支持两种状态类型以实现向后兼容：
 * 1. AgentState (旧版) - 用于现有工具
 * 2. UnifiedAgentState (新版) - 用于新工具
 */
public interface UnifiedAgentTool {
    
    /**
     * 执行工具 (旧版接口，保持向后兼容)
     * 
     * @param state Agent 状态 (旧版)
     * @return 执行结果
     */
    default ActionResult execute(AgentState state) {
        throw new UnsupportedOperationException(
            "This tool does not support old AgentState. Please use UnifiedAgentState.");
    }
    
    /**
     * 执行工具 (新版接口)
     * 
     * @param state Agent 状态 (新版)
     * @return 执行结果
     */
    default ActionResult execute(UnifiedAgentState state) {
        throw new UnsupportedOperationException(
            "This tool does not support UnifiedAgentState. Please use old AgentState.");
    }
    
    /**
     * 工具名称
     */
    String getToolName();
    
    /**
     * 工具描述
     */
    String getDescription();
}
