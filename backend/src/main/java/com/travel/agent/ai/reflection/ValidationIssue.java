package com.travel.agent.ai.reflection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 验证问题
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationIssue implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private IssueCategory category;
    private IssueSeverity severity;
    private String message;
    private String suggestion;
    private Integer dayNumber;
    private List<String> details;
}
