package com.travel.agent.evaluation;

import com.travel.agent.dto.unified.UnifiedTravelIntent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 推荐测试用例
 * 用于评估推荐系统的准确性
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationTestCase {
    
    /**
     * 测试用例名称
     */
    private String testCaseName;
    
    /**
     * 用户输入
     */
    private String userInput;
    
    /**
     * 期望的意图
     */
    private UnifiedTravelIntent expectedIntent;
    
    /**
     * 期望的推荐区域
     */
    private List<String> expectedRegions;
    
    /**
     * 预算下限（澳元）
     */
    private Integer budgetMin;
    
    /**
     * 预算上限（澳元）
     */
    private Integer budgetMax;
    
    /**
     * 期望的兴趣标签
     */
    private List<String> interests;
    
    /**
     * 测试难度：easy, medium, hard
     */
    private String difficulty;
    
    /**
     * 期望的目的地（用于精确匹配测试）
     */
    private String expectedDestination;
    
    /**
     * 期望的天数（允许误差范围）
     */
    private Integer expectedDays;
    
    /**
     * 期望的预算（允许误差范围）
     */
    private Integer expectedBudget;
}
