package com.travel.agent.ai.reflection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 反思结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReflectionResult implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private List<ValidationIssue> issues;
    private Boolean approved;
    private Long criticalCount;
    private Long warningCount;
    private Long suggestionCount;
}
