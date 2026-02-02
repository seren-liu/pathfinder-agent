package com.travel.agent.ai.tools;

import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.Map;

/**
 * 统一工具接口
 * 所有Agent工具必须实现此接口
 */
public interface AgentTool {
    
    /**
     * 工具名称（唯一标识）
     */
    String getName();
    
    /**
     * 工具描述（供LLM理解工具用途）
     */
    String getDescription();
    
    /**
     * 工具规格（参数定义）
     */
    ToolSpecification getSpecification();
    
    /**
     * 执行工具
     * @param parameters 工具参数
     * @return 执行结果
     */
    Object execute(Map<String, Object> parameters);
    
    /**
     * 工具类别（用于分组和过滤）
     */
    default ToolCategory getCategory() {
        return ToolCategory.GENERAL;
    }
    
    /**
     * 是否支持并行执行
     */
    default boolean isParallelizable() {
        return true;
    }
}
