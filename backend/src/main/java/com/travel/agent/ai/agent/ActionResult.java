package com.travel.agent.ai.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具执行结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionResult {
    
    /** 工具名称 */
    private String toolName;
    
    /** 是否成功 */
    private Boolean success;
    
    /** 观察结果 */
    private String observation;
    
    /** 执行结果数据 */
    private Object result;
    
    /** 错误信息 */
    private String error;
    
    /** 执行时长（毫秒） */
    private Long durationMs;
}
