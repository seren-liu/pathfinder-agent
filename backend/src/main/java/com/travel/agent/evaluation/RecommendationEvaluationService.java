package com.travel.agent.evaluation;

import com.travel.agent.ai.state.RecommendationState;
import com.travel.agent.dto.unified.UnifiedTravelIntent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 推荐评估服务
 * 评估推荐系统的准确性和质量
 */
@Slf4j
@Service
public class RecommendationEvaluationService {
    
    /**
     * 计算意图匹配准确率
     */
    public double calculateIntentMatchAccuracy(List<RecommendationTestCase> testCases,
                                               Map<String, UnifiedTravelIntent> actualIntents) {
        int correct = 0;
        int total = 0;
        
        for (RecommendationTestCase testCase : testCases) {
            UnifiedTravelIntent actualIntent = actualIntents.get(testCase.getTestCaseName());
            if (actualIntent == null) {
                continue;
            }
            
            total++;
            
            if (matchesIntent(actualIntent, testCase)) {
                correct++;
                log.debug("✅ Intent match: {}", testCase.getTestCaseName());
            } else {
                log.debug("❌ Intent mismatch: {}", testCase.getTestCaseName());
            }
        }
        
        double accuracy = total > 0 ? (double) correct / total : 0.0;
        log.info("📊 Intent Match Accuracy: {}/{} = {}", correct, total, accuracy);
        
        return accuracy;
    }
    
    /**
     * 检查意图是否匹配
     */
    private boolean matchesIntent(UnifiedTravelIntent actual, RecommendationTestCase testCase) {
        // 检查目的地匹配（如果有期望目的地）
        if (testCase.getExpectedDestination() != null) {
            if (actual.getDestination() == null || 
                !actual.getDestination().toLowerCase().contains(testCase.getExpectedDestination().toLowerCase())) {
                return false;
            }
        }
        
        // 检查天数匹配（允许 ±1 天误差）
        if (testCase.getExpectedDays() != null && actual.getDays() != null) {
            if (Math.abs(actual.getDays() - testCase.getExpectedDays()) > 1) {
                return false;
            }
        }
        
        // 检查预算匹配（允许 ±15% 误差）
        if (testCase.getExpectedBudget() != null && actual.getBudget() != null) {
            double actualBudget = actual.getBudget().doubleValue();
            double expectedBudget = testCase.getExpectedBudget().doubleValue();
            double diff = Math.abs(actualBudget - expectedBudget);
            if (diff / expectedBudget > 0.15) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 计算预算匹配准确率
     */
    public double calculateBudgetMatchAccuracy(List<RecommendationTestCase> testCases,
                                               Map<String, List<Map<String, Object>>> recommendations) {
        int correct = 0;
        int total = 0;
        
        for (RecommendationTestCase testCase : testCases) {
            if (testCase.getBudgetMin() == null || testCase.getBudgetMax() == null) {
                continue;
            }
            
            List<Map<String, Object>> recs = recommendations.get(testCase.getTestCaseName());
            if (recs == null || recs.isEmpty()) {
                continue;
            }
            
            total++;
            
            // 检查所有推荐是否在预算范围内
            boolean allInBudget = recs.stream().allMatch(rec -> {
                Object costObj = rec.get("estimatedCost");
                if (costObj == null) return true;
                
                int cost = costObj instanceof Integer ? (Integer) costObj : 
                          Integer.parseInt(costObj.toString());
                
                return cost >= testCase.getBudgetMin() && cost <= testCase.getBudgetMax();
            });
            
            if (allInBudget) {
                correct++;
                log.debug("✅ Budget match: {}", testCase.getTestCaseName());
            } else {
                log.debug("❌ Budget mismatch: {}", testCase.getTestCaseName());
            }
        }
        
        double accuracy = total > 0 ? (double) correct / total : 0.0;
        log.info("📊 Budget Match Accuracy: {}/{} = {}", correct, total, accuracy);
        
        return accuracy;
    }
    
    /**
     * 计算区域匹配准确率
     */
    public double calculateRegionMatchAccuracy(List<RecommendationTestCase> testCases,
                                               Map<String, List<Map<String, Object>>> recommendations) {
        int correct = 0;
        int total = 0;
        
        for (RecommendationTestCase testCase : testCases) {
            if (testCase.getExpectedRegions() == null || testCase.getExpectedRegions().isEmpty()) {
                continue;
            }
            
            List<Map<String, Object>> recs = recommendations.get(testCase.getTestCaseName());
            if (recs == null || recs.isEmpty()) {
                continue;
            }
            
            total++;
            
            // 检查推荐是否在期望区域内
            boolean allInRegion = recs.stream().allMatch(rec -> {
                String name = (String) rec.get("name");
                String country = (String) rec.get("country");
                
                return testCase.getExpectedRegions().stream().anyMatch(region -> 
                    (name != null && name.toLowerCase().contains(region.toLowerCase())) ||
                    (country != null && country.toLowerCase().contains(region.toLowerCase()))
                );
            });
            
            if (allInRegion) {
                correct++;
                log.debug("✅ Region match: {}", testCase.getTestCaseName());
            } else {
                log.debug("❌ Region mismatch: {}", testCase.getTestCaseName());
            }
        }
        
        double accuracy = total > 0 ? (double) correct / total : 0.0;
        log.info("📊 Region Match Accuracy: {}/{} = {}", correct, total, accuracy);
        
        return accuracy;
    }
    
    /**
     * 计算推荐多样性分数
     */
    public double calculateDiversityScore(List<Map<String, Object>> recommendations) {
        if (recommendations == null || recommendations.isEmpty()) {
            return 0.0;
        }
        
        // 计算国家多样性
        Set<String> uniqueCountries = recommendations.stream()
            .map(rec -> (String) rec.get("country"))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        
        double countryDiversity = (double) uniqueCountries.size() / recommendations.size();
        
        // 计算特征多样性
        Set<String> allFeatures = new HashSet<>();
        int totalFeatures = 0;
        
        for (Map<String, Object> rec : recommendations) {
            @SuppressWarnings("unchecked")
            List<String> features = (List<String>) rec.get("features");
            if (features != null) {
                allFeatures.addAll(features);
                totalFeatures += features.size();
            }
        }
        
        double featureDiversity = totalFeatures > 0 ? 
            (double) allFeatures.size() / totalFeatures : 0.0;
        
        // 综合多样性分数（国家 50% + 特征 50%）
        double diversityScore = countryDiversity * 0.5 + featureDiversity * 0.5;
        
        log.debug("📊 Diversity - Countries: {}, Features: {}, Score: {}", 
                 uniqueCountries.size(), allFeatures.size(), diversityScore);
        
        return diversityScore;
    }
    
    /**
     * 计算平均多样性分数
     */
    public double calculateAverageDiversity(Map<String, List<Map<String, Object>>> allRecommendations) {
        if (allRecommendations == null || allRecommendations.isEmpty()) {
            return 0.0;
        }
        
        double totalDiversity = allRecommendations.values().stream()
            .mapToDouble(this::calculateDiversityScore)
            .sum();
        
        double avgDiversity = totalDiversity / allRecommendations.size();
        log.info("📊 Average Diversity Score: {}", avgDiversity);
        
        return avgDiversity;
    }
    
    /**
     * 生成评估报告
     */
    public Map<String, Object> generateEvaluationReport(
            List<RecommendationTestCase> testCases,
            Map<String, UnifiedTravelIntent> actualIntents,
            Map<String, List<Map<String, Object>>> recommendations) {
        
        Map<String, Object> report = new HashMap<>();
        
        // 计算各项指标
        double intentAccuracy = calculateIntentMatchAccuracy(testCases, actualIntents);
        double budgetAccuracy = calculateBudgetMatchAccuracy(testCases, recommendations);
        double regionAccuracy = calculateRegionMatchAccuracy(testCases, recommendations);
        double avgDiversity = calculateAverageDiversity(recommendations);
        
        report.put("intentMatchAccuracy", intentAccuracy);
        report.put("budgetMatchAccuracy", budgetAccuracy);
        report.put("regionMatchAccuracy", regionAccuracy);
        report.put("averageDiversity", avgDiversity);
        report.put("totalTestCases", testCases.size());
        report.put("timestamp", new Date());
        
        // 计算总体评分（加权平均）
        double overallScore = intentAccuracy * 0.3 + 
                             budgetAccuracy * 0.3 + 
                             regionAccuracy * 0.3 + 
                             avgDiversity * 0.1;
        
        report.put("overallScore", overallScore);
        
        log.info("📊 Evaluation Report Generated:");
        log.info("  - Intent Accuracy: {}%", intentAccuracy * 100);
        log.info("  - Budget Accuracy: {}%", budgetAccuracy * 100);
        log.info("  - Region Accuracy: {}%", regionAccuracy * 100);
        log.info("  - Avg Diversity: {}", avgDiversity);
        log.info("  - Overall Score: {}%", overallScore * 100);
        
        return report;
    }
}
