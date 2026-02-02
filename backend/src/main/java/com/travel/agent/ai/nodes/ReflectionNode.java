package com.travel.agent.ai.nodes;

import com.travel.agent.ai.reflection.EnhancedReflectionService;
import com.travel.agent.ai.reflection.IssueSeverity;
import com.travel.agent.ai.reflection.ReflectionResult;
import com.travel.agent.ai.reflection.ValidationIssue;
import com.travel.agent.ai.state.TravelPlanningState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 反思节点（增强版）
 * 使用 EnhancedReflectionService 进行多维度质量验证
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReflectionNode implements AsyncNodeAction<TravelPlanningState> {
    
    private final EnhancedReflectionService reflectionService;
    
    @Override
    public CompletableFuture<Map<String, Object>> apply(TravelPlanningState state) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("🤔 Enhanced Reflection Node: Starting comprehensive validation");
            
            // 使用增强的反思服务进行验证
            ReflectionResult result = reflectionService.validate(state);
            
            int reflectionCount = (state.getReflectionCount() == null ? 0 : state.getReflectionCount()) + 1;
            
            // 提取问题描述
            List<String> issueMessages = result.getIssues().stream()
                .map(issue -> String.format("[%s] %s: %s", 
                    issue.getSeverity(), 
                    issue.getCategory(), 
                    issue.getMessage()))
                .collect(Collectors.toList());
            
            // 提取修正建议
            List<String> suggestions = result.getIssues().stream()
                .filter(issue -> issue.getSeverity() == IssueSeverity.CRITICAL)
                .map(ValidationIssue::getSuggestion)
                .collect(Collectors.toList());
            
            log.info("🤔 Enhanced Reflection completed: {} issues (Critical: {}, Warning: {}, Suggestion: {}), approved: {}", 
                    result.getIssues().size(),
                    result.getCriticalCount(),
                    result.getWarningCount(),
                    result.getSuggestionCount(),
                    result.getApproved());
            
            Map<String, Object> updates = new HashMap<>();
            updates.put("issues", issueMessages);
            updates.put("reflectionCount", reflectionCount);
            updates.put("approved", result.getApproved());
            updates.put("revisionSuggestions", suggestions);
            updates.put("currentStep", result.getApproved() 
                ? "Enhanced validation passed" 
                : "Issues found, needs revision");
            updates.put("progress", 80);
            updates.put("progressMessage", result.getApproved() 
                ? String.format("Itinerary validated successfully (%d checks passed)", 
                    result.getIssues().size() - result.getCriticalCount())
                : String.format("Found %d critical issues, revising...", result.getCriticalCount()));
            
            // 保存详细的验证结果到 metadata
            Map<String, Object> metadata = new HashMap<>();
            if (state.getMetadata() != null) {
                metadata.putAll(state.getMetadata());
            }
            metadata.put("validationResult", result);
            updates.put("metadata", metadata);
            
            return updates;
        });
    }
}
