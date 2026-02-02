package com.travel.agent.ai.nodes;

import com.travel.agent.ai.state.TravelPlanningState;
import com.travel.agent.ai.tools.AttractionInfo;
import com.travel.agent.ai.tools.BudgetValidation;
import com.travel.agent.ai.tools.BudgetValidationTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 预算验证节点
 * 验证景点成本是否在预算内
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BudgetValidationNode implements AsyncNodeAction<TravelPlanningState> {
    
    private final BudgetValidationTool budgetTool;
    
    @Override
    public CompletableFuture<Map<String, Object>> apply(TravelPlanningState state) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("💰 Budget Validation Node: Validating budget ${}", state.getBudget());
            
            try {
                // 转换 attractions 为 AttractionInfo 对象
                List<AttractionInfo> attractions = new ArrayList<>();
                if (state.getAttractions() != null) {
                    for (Map<String, Object> attrMap : state.getAttractions()) {
                        AttractionInfo attr = AttractionInfo.builder()
                            .name((String) attrMap.get("name"))
                            .category((String) attrMap.get("category"))
                            .price((String) attrMap.get("price"))
                            .description((String) attrMap.get("description"))
                            .relevanceScore((Double) attrMap.get("relevanceScore"))
                            .city((String) attrMap.get("city"))
                            .build();
                        attractions.add(attr);
                    }
                }
                
                // 调用预算验证工具
                BudgetValidation validation = budgetTool.validate(
                    attractions,
                    state.getBudget()
                );
                
                if (validation.getWithinBudget()) {
                    log.info("✅ Budget validation passed: ${} / ${}", 
                            validation.getTotalCost(), validation.getBudget());
                } else {
                    log.warn("⚠️ Budget exceeded: ${} > ${}", 
                            validation.getTotalCost(), validation.getBudget());
                }
                
                // 转换为 Map
                Map<String, Object> budgetCheckMap = new HashMap<>();
                budgetCheckMap.put("totalCost", validation.getTotalCost());
                budgetCheckMap.put("budget", validation.getBudget());
                budgetCheckMap.put("withinBudget", validation.getWithinBudget());
                budgetCheckMap.put("remaining", validation.getRemaining());
                budgetCheckMap.put("recommendations", validation.getRecommendations());
                
                return Map.of(
                    "budgetCheck", budgetCheckMap,
                    "currentStep", "Budget validation completed",
                    "progress", 40,
                    "progressMessage", validation.getWithinBudget() 
                        ? "Budget validation passed" 
                        : "Budget exceeded, will adjust"
                );
                
            } catch (Exception e) {
                log.error("❌ Budget validation failed", e);
                return Map.of(
                    "errorMessage", "Budget validation failed: " + e.getMessage()
                );
            }
        });
    }
}
