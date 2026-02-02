package com.travel.agent.ai.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ReAct 循环中的单个步骤
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReActStep {
    
    /** 迭代次数 */
    private Integer iteration;
    
    /** 思考内容 */
    private String thought;
    
    /** 执行的动作 */
    private String action;
    
    /** 观察到的结果 */
    private String observation;
    
    /** 是否成功 */
    private Boolean success;
}
