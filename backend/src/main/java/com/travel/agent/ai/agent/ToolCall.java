package com.travel.agent.ai.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 工具调用信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {
    
    /** 工具名称 */
    private String toolName;
    
    /** 工具参数 */
    private Map<String, Object> parameters;
}
