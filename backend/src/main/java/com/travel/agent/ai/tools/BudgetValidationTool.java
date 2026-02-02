package com.travel.agent.ai.tools;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 预算验证工具
 * 验证行程成本是否在预算范围内
 */
@Slf4j
@Component
public class BudgetValidationTool implements AgentTool {
    
    @Override
    public String getName() {
        return "validate_budget";
    }
    
    @Override
    public String getDescription() {
        return "Validates if the total cost of attractions and activities fits within the budget. " +
               "Calculates total cost, checks budget compliance, and suggests alternatives if over budget.";
    }
    
    @Override
    public ToolCategory getCategory() {
        return ToolCategory.VALIDATION;
    }
    
    /**
     * 验证预算
     */
    @Tool("Calculate total cost and validate against budget")
    public BudgetValidation validate(
        @P("list of attractions with prices") List<AttractionInfo> attractions,
        @P("total budget in AUD") BigDecimal budget
    ) {
        log.info("💰 Budget Tool: Validating {} attractions against ${}", 
                 attractions.size(), budget);
        
        // 计算总成本
        BigDecimal totalCost = BigDecimal.ZERO;
        List<CostItem> breakdown = new ArrayList<>();
        
        for (AttractionInfo attraction : attractions) {
            BigDecimal cost = parseCost(attraction.getPrice());
            totalCost = totalCost.add(cost);
            
            breakdown.add(CostItem.builder()
                .name(attraction.getName())
                .category(attraction.getCategory())
                .cost(cost)
                .build());
        }
        
        boolean withinBudget = totalCost.compareTo(budget) <= 0;
        BigDecimal remaining = budget.subtract(totalCost);
        
        // 如果超预算，生成建议
        List<String> recommendations = null;
        if (!withinBudget) {
            recommendations = suggestAlternatives(attractions, budget, totalCost);
        }
        
        return BudgetValidation.builder()
            .totalCost(totalCost)
            .budget(budget)
            .withinBudget(withinBudget)
            .remaining(remaining)
            .costBreakdown(breakdown)
            .recommendations(recommendations)
            .build();
    }
    
    /**
     * 解析价格字符串
     */
    private BigDecimal parseCost(String priceStr) {
        if (priceStr == null || priceStr.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        // 提取数字（支持 ¥, $, €, £ 等货币符号）
        Pattern pattern = Pattern.compile("[¥$€£]?\\s*(\\d+[,\\d]*)");
        Matcher matcher = pattern.matcher(priceStr);
        
        if (matcher.find()) {
            String numStr = matcher.group(1).replace(",", "");
            try {
                return new BigDecimal(numStr);
            } catch (NumberFormatException e) {
                log.warn("Failed to parse price: {}", priceStr);
                return BigDecimal.ZERO;
            }
        }
        
        return BigDecimal.ZERO;
    }
    
    /**
     * 生成预算优化建议
     */
    private List<String> suggestAlternatives(List<AttractionInfo> attractions, 
                                             BigDecimal budget, 
                                             BigDecimal totalCost) {
        List<String> suggestions = new ArrayList<>();
        BigDecimal overage = totalCost.subtract(budget);
        
        suggestions.add(String.format("Budget exceeded by $%.2f", overage));
        suggestions.add("Consider these alternatives:");
        suggestions.add("• Replace expensive attractions with free alternatives (parks, temples, beaches)");
        suggestions.add("• Reduce dining budget by trying local street food or markets");
        suggestions.add("• Use public transportation instead of taxis");
        suggestions.add("• Choose budget-friendly accommodation options");
        
        // 找出最贵的项目
        attractions.stream()
            .sorted((a, b) -> parseCost(b.getPrice()).compareTo(parseCost(a.getPrice())))
            .limit(3)
            .forEach(a -> suggestions.add(
                String.format("• Consider cheaper alternative to %s (%s)", 
                    a.getName(), a.getPrice())
            ));
        
        return suggestions;
    }
    
    @Override
    public Object execute(Map<String, Object> parameters) {
        @SuppressWarnings("unchecked")
        List<AttractionInfo> attractions = (List<AttractionInfo>) parameters.get("attractions");
        BigDecimal budget = new BigDecimal(parameters.get("budget").toString());
        return validate(attractions, budget);
    }
    
    @Override
    public ToolSpecification getSpecification() {
        // Auto-generate from @Tool annotated methods
        List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(this);
        return specs.isEmpty() ? null : specs.get(0);
    }
}
