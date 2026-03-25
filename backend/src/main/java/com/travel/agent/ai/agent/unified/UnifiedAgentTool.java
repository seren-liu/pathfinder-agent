package com.travel.agent.ai.agent.unified;

import com.travel.agent.ai.agent.ActionResult;

/**
 * 统一 Agent 工具接口
 * 用于 UnifiedReActAgent 的工具，包装现有服务
 */
public interface UnifiedAgentTool {

    /**
     * 执行工具 (新版接口)
     *
     * @param state Agent 状态 (新版)
     * @return 执行结果
     */
    ActionResult execute(UnifiedAgentState state);

    /**
     * 工具名称
     */
    String getToolName();

    /**
     * 工具描述
     */
    String getDescription();
}
