package com.travel.agent.ai.reflection;

import com.travel.agent.ai.state.ActivityPlan;
import com.travel.agent.ai.state.BudgetCheck;
import com.travel.agent.ai.state.DayPlan;
import com.travel.agent.ai.state.TravelPlanningState;
import com.travel.agent.ai.tools.Coordinates;
import com.travel.agent.service.AIService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 增强的反思服务
 * 多维度验证行程质量
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnhancedReflectionService {
    
    private final AIService aiService;
    
    /**
     * 全面验证行程
     */
    public ReflectionResult validate(TravelPlanningState state) {
        log.info("🤔 Enhanced Reflection: Starting comprehensive validation");
        
        List<ValidationIssue> issues = new ArrayList<>();
        
        // 1. 基础验证
        issues.addAll(validateBasics(state));
        
        // 2. 预算验证
        issues.addAll(validateBudget(state));
        
        // 3. 时间合理性验证
        issues.addAll(validateTiming(state));
        
        // 4. 地理位置合理性验证
        issues.addAll(validateGeography(state));
        
        // 5. 活动多样性验证
        issues.addAll(validateDiversity(state));
        
        // 6. LLM 深度验证
        issues.addAll(validateWithLLM(state));
        
        // 按严重程度排序
        issues.sort((a, b) -> b.getSeverity().compareTo(a.getSeverity()));
        
        boolean approved = issues.stream()
            .noneMatch(issue -> issue.getSeverity() == IssueSeverity.CRITICAL);
        
        log.info("🤔 Reflection completed: {} issues found (approved: {})", 
                issues.size(), approved);
        
        return ReflectionResult.builder()
            .issues(issues)
            .approved(approved)
            .criticalCount(countBySeverity(issues, IssueSeverity.CRITICAL))
            .warningCount(countBySeverity(issues, IssueSeverity.WARNING))
            .suggestionCount(countBySeverity(issues, IssueSeverity.SUGGESTION))
            .build();
    }
    
    /**
     * 1. 基础验证
     */
    private List<ValidationIssue> validateBasics(TravelPlanningState state) {
        List<ValidationIssue> issues = new ArrayList<>();
        
        // 检查行程是否为空
        if (state.getItinerary() == null || state.getItinerary().isEmpty()) {
            issues.add(ValidationIssue.builder()
                .category(IssueCategory.STRUCTURE)
                .severity(IssueSeverity.CRITICAL)
                .message("Itinerary is empty")
                .suggestion("Generate a complete itinerary with all days")
                .build());
            return issues;
        }
        
        // 检查天数是否匹配
        if (state.getItinerary().size() != state.getDurationDays()) {
            issues.add(ValidationIssue.builder()
                .category(IssueCategory.STRUCTURE)
                .severity(IssueSeverity.CRITICAL)
                .message(String.format("Expected %d days, got %d days",
                    state.getDurationDays(), state.getItinerary().size()))
                .suggestion(String.format("Adjust itinerary to exactly %d days", 
                    state.getDurationDays()))
                .build());
        }
        
        // 检查每天是否有活动
        for (DayPlan day : state.getItineraryTyped()) {
            if (day.getActivities() == null || day.getActivities().isEmpty()) {
                issues.add(ValidationIssue.builder()
                    .category(IssueCategory.STRUCTURE)
                    .severity(IssueSeverity.CRITICAL)
                    .message(String.format("Day %d has no activities", day.getDayNumber()))
                    .suggestion("Add at least 3-4 activities for this day")
                    .dayNumber(day.getDayNumber())
                    .build());
            } else if (day.getActivities().size() < 3) {
                issues.add(ValidationIssue.builder()
                    .category(IssueCategory.STRUCTURE)
                    .severity(IssueSeverity.WARNING)
                    .message(String.format("Day %d has only %d activities (recommended: 4+)",
                        day.getDayNumber(), day.getActivities().size()))
                    .suggestion("Add more activities to make the day fuller")
                    .dayNumber(day.getDayNumber())
                    .build());
            }
        }
        
        return issues;
    }
    
    /**
     * 2. 预算验证
     */
    private List<ValidationIssue> validateBudget(TravelPlanningState state) {
        List<ValidationIssue> issues = new ArrayList<>();
        
        BudgetCheck budgetCheck = state.getBudgetCheckTyped();
        if (budgetCheck == null || budgetCheck.getWithinBudget() == null) {
            issues.add(ValidationIssue.builder()
                .category(IssueCategory.BUDGET)
                .severity(IssueSeverity.WARNING)
                .message("Budget not validated")
                .suggestion("Run budget validation before finalizing")
                .build());
            return issues;
        }
        
        // 检查是否超预算
        if (!budgetCheck.getWithinBudget()) {
            BigDecimal overage = budgetCheck.getTotalCost()
                .subtract(budgetCheck.getBudget());
            
            issues.add(ValidationIssue.builder()
                .category(IssueCategory.BUDGET)
                .severity(IssueSeverity.CRITICAL)
                .message(String.format("Budget exceeded by $%.2f", overage))
                .suggestion("Replace expensive activities with budget-friendly alternatives")
                .details(budgetCheck.getRecommendations())
                .build());
        }
        
        // 检查预算利用率
        BigDecimal utilizationRate = budgetCheck.getTotalCost()
            .divide(budgetCheck.getBudget(), 2, RoundingMode.HALF_UP);
        
        if (utilizationRate.compareTo(new BigDecimal("0.5")) < 0) {
            issues.add(ValidationIssue.builder()
                .category(IssueCategory.BUDGET)
                .severity(IssueSeverity.SUGGESTION)
                .message(String.format("Budget utilization is low (%.0f%%)", 
                    utilizationRate.multiply(new BigDecimal("100"))))
                .suggestion("Consider adding more premium experiences or extending activities")
                .build());
        }
        
        return issues;
    }
    
    /**
     * 3. 时间合理性验证
     */
    private List<ValidationIssue> validateTiming(TravelPlanningState state) {
        List<ValidationIssue> issues = new ArrayList<>();
        
        for (DayPlan day : state.getItineraryTyped()) {
            if (day.getActivities() == null) continue;
            
            LocalTime previousEnd = LocalTime.of(0, 0);
            
            for (int i = 0; i < day.getActivities().size(); i++) {
                ActivityPlan activity = day.getActivities().get(i);
                
                if (activity.getStartTime() == null) {
                    issues.add(ValidationIssue.builder()
                        .category(IssueCategory.TIMING)
                        .severity(IssueSeverity.WARNING)
                        .message(String.format("Day %d, Activity '%s': Missing start time",
                            day.getDayNumber(), activity.getName()))
                        .suggestion("Add a specific start time")
                        .dayNumber(day.getDayNumber())
                        .build());
                    continue;
                }
                
                LocalTime start = LocalTime.parse(activity.getStartTime());
                
                // 检查时间重叠
                if (start.isBefore(previousEnd)) {
                    issues.add(ValidationIssue.builder()
                        .category(IssueCategory.TIMING)
                        .severity(IssueSeverity.CRITICAL)
                        .message(String.format("Day %d: Activity '%s' overlaps with previous activity",
                            day.getDayNumber(), activity.getName()))
                        .suggestion("Adjust start time to avoid overlap")
                        .dayNumber(day.getDayNumber())
                        .build());
                }
                
                // 检查时间合理性（6:00 - 23:00）
                if (start.isBefore(LocalTime.of(6, 0)) || start.isAfter(LocalTime.of(23, 0))) {
                    issues.add(ValidationIssue.builder()
                        .category(IssueCategory.TIMING)
                        .severity(IssueSeverity.WARNING)
                        .message(String.format("Day %d: Activity '%s' has unusual time %s",
                            day.getDayNumber(), activity.getName(), start))
                        .suggestion("Consider scheduling between 6:00 and 23:00")
                        .dayNumber(day.getDayNumber())
                        .build());
                }
                
                // 检查活动时长合理性
                if (activity.getDurationMinutes() != null) {
                    if (activity.getDurationMinutes() < 30) {
                        issues.add(ValidationIssue.builder()
                            .category(IssueCategory.TIMING)
                            .severity(IssueSeverity.SUGGESTION)
                            .message(String.format("Day %d: Activity '%s' duration is very short (%d min)",
                                day.getDayNumber(), activity.getName(), activity.getDurationMinutes()))
                            .suggestion("Consider extending the duration")
                            .dayNumber(day.getDayNumber())
                            .build());
                    } else if (activity.getDurationMinutes() > 480) {
                        issues.add(ValidationIssue.builder()
                            .category(IssueCategory.TIMING)
                            .severity(IssueSeverity.WARNING)
                            .message(String.format("Day %d: Activity '%s' duration is very long (%d min)",
                                day.getDayNumber(), activity.getName(), activity.getDurationMinutes()))
                            .suggestion("Consider breaking into multiple activities")
                            .dayNumber(day.getDayNumber())
                            .build());
                    }
                    
                    previousEnd = start.plusMinutes(activity.getDurationMinutes());
                }
            }
        }
        
        return issues;
    }
    
    /**
     * 4. 地理位置合理性验证
     */
    private List<ValidationIssue> validateGeography(TravelPlanningState state) {
        List<ValidationIssue> issues = new ArrayList<>();
        
        Map<String, Coordinates> geoData = state.getGeoDataTyped();
        if (geoData == null || geoData.isEmpty()) {
            issues.add(ValidationIssue.builder()
                .category(IssueCategory.GEOGRAPHY)
                .severity(IssueSeverity.WARNING)
                .message("Locations not geocoded")
                .suggestion("Geocode locations for better validation")
                .build());
            return issues;
        }
        
        for (DayPlan day : state.getItineraryTyped()) {
            if (day.getActivities() == null || day.getActivities().size() < 2) continue;
            
            for (int i = 0; i < day.getActivities().size() - 1; i++) {
                ActivityPlan current = day.getActivities().get(i);
                ActivityPlan next = day.getActivities().get(i + 1);
                
                Coordinates coord1 = geoData.get(current.getLocation());
                Coordinates coord2 = geoData.get(next.getLocation());
                
                if (coord1 != null && coord2 != null && 
                    coord1.getSuccess() && coord2.getSuccess()) {
                    
                    // 计算距离
                    double distance = calculateDistance(
                        coord1.getLatitude().doubleValue(),
                        coord1.getLongitude().doubleValue(),
                        coord2.getLatitude().doubleValue(),
                        coord2.getLongitude().doubleValue()
                    );
                    
                    // 计算时间间隔
                    int timeGap = calculateTimeGap(current, next);
                    
                    // 检查是否有足够时间移动
                    // 假设平均速度 20 km/h（考虑交通）
                    int requiredMinutes = (int) (distance / 20.0 * 60);
                    
                    if (distance > 20 && timeGap < requiredMinutes) {
                        issues.add(ValidationIssue.builder()
                            .category(IssueCategory.GEOGRAPHY)
                            .severity(IssueSeverity.CRITICAL)
                            .message(String.format(
                                "Day %d: Not enough time to travel %.1f km from '%s' to '%s' (need %d min, have %d min)",
                                day.getDayNumber(), distance, 
                                current.getName(), next.getName(),
                                requiredMinutes, timeGap))
                            .suggestion("Add more time between activities or choose closer locations")
                            .dayNumber(day.getDayNumber())
                            .build());
                    }
                }
            }
        }
        
        return issues;
    }
    
    /**
     * 5. 活动多样性验证
     */
    private List<ValidationIssue> validateDiversity(TravelPlanningState state) {
        List<ValidationIssue> issues = new ArrayList<>();
        
        // 统计活动类型
        Map<String, Integer> typeCount = new HashMap<>();
        
        for (DayPlan day : state.getItineraryTyped()) {
            if (day.getActivities() == null) continue;
            
            for (ActivityPlan activity : day.getActivities()) {
                String type = activity.getType();
                typeCount.put(type, typeCount.getOrDefault(type, 0) + 1);
            }
        }
        
        // 检查是否过于单一
        int totalActivities = typeCount.values().stream().mapToInt(Integer::intValue).sum();
        
        for (var entry : typeCount.entrySet()) {
            double ratio = (double) entry.getValue() / totalActivities;
            
            if (ratio > 0.6) {
                issues.add(ValidationIssue.builder()
                    .category(IssueCategory.DIVERSITY)
                    .severity(IssueSeverity.WARNING)
                    .message(String.format("Activity type '%s' is overrepresented (%.0f%%)",
                        entry.getKey(), ratio * 100))
                    .suggestion("Add more variety to the itinerary")
                    .build());
            }
        }
        
        // 检查是否缺少必要类型
        if (!typeCount.containsKey("accommodation")) {
            issues.add(ValidationIssue.builder()
                .category(IssueCategory.DIVERSITY)
                .severity(IssueSeverity.SUGGESTION)
                .message("No accommodation activities found")
                .suggestion("Add accommodation information for each day")
                .build());
        }
        
        if (!typeCount.containsKey("dining")) {
            issues.add(ValidationIssue.builder()
                .category(IssueCategory.DIVERSITY)
                .severity(IssueSeverity.SUGGESTION)
                .message("No dining activities found")
                .suggestion("Add meal plans to the itinerary")
                .build());
        }
        
        return issues;
    }
    
    /**
     * 6. LLM 深度验证
     */
    private List<ValidationIssue> validateWithLLM(TravelPlanningState state) {
        List<ValidationIssue> issues = new ArrayList<>();
        
        try {
            String prompt = buildLLMValidationPrompt(state);
            String response = aiService.chat(prompt);
            
            // 解析 LLM 响应
            if (!response.toLowerCase().contains("pass")) {
                issues.add(ValidationIssue.builder()
                    .category(IssueCategory.QUALITY)
                    .severity(IssueSeverity.WARNING)
                    .message("LLM validation found issues")
                    .suggestion(response)
                    .build());
            }
            
        } catch (Exception e) {
            log.error("LLM validation failed", e);
        }
        
        return issues;
    }
    
    /**
     * 构建 LLM 验证 Prompt
     */
    private String buildLLMValidationPrompt(TravelPlanningState state) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("""
            You are a travel expert reviewing an itinerary. Analyze the following itinerary and identify any issues:
            
            """);
        
        prompt.append(String.format("""
            Destination: %s, %s
            Duration: %d days
            Budget: $%s AUD
            
            """,
            state.getDestination(),
            state.getDestinationCountry(),
            state.getDurationDays(),
            state.getBudget()
        ));
        
        // 添加行程详情
        for (DayPlan day : state.getItineraryTyped()) {
            prompt.append(String.format("\nDay %d - %s:\n", day.getDayNumber(), day.getTheme()));
            
            if (day.getActivities() != null) {
                for (ActivityPlan activity : day.getActivities()) {
                    prompt.append(String.format("  %s: %s (%s, %d min, $%s)\n",
                        activity.getStartTime(),
                        activity.getName(),
                        activity.getType(),
                        activity.getDurationMinutes(),
                        activity.getEstimatedCost()
                    ));
                }
            }
        }
        
        prompt.append("""
            
            Check for:
            1. Logical flow and pacing
            2. Variety of activities
            3. Cultural appropriateness
            4. Practical feasibility
            5. Value for money
            
            If everything looks good, respond with "PASS".
            Otherwise, list the issues concisely (max 3 issues).
            """);
        
        return prompt.toString();
    }
    
    /**
     * 计算两点间距离（Haversine 公式）
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // 地球半径（公里）
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }
    
    /**
     * 计算两个活动之间的时间间隔（分钟）
     */
    private int calculateTimeGap(ActivityPlan current, ActivityPlan next) {
        if (current.getStartTime() == null || next.getStartTime() == null ||
            current.getDurationMinutes() == null) {
            return 0;
        }
        
        LocalTime currentEnd = LocalTime.parse(current.getStartTime())
            .plusMinutes(current.getDurationMinutes());
        LocalTime nextStart = LocalTime.parse(next.getStartTime());
        
        return (int) java.time.Duration.between(currentEnd, nextStart).toMinutes();
    }
    
    /**
     * 统计特定严重程度的问题数量
     */
    private long countBySeverity(List<ValidationIssue> issues, IssueSeverity severity) {
        return issues.stream()
            .filter(issue -> issue.getSeverity() == severity)
            .count();
    }
}
