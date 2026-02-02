package com.travel.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 推荐的目的地结果（AI 直接生成，不依赖数据库）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIDestinationRecommendation {
    /**
     * 目的地 ID（如果在数据库中存在）
     */
    private Long destinationId;
    
    /**
     * 目的地名称
     */
    private String destinationName;
    
    /**
     * 国家
     */
    private String country;
    
    /**
     * 州/省（可选）
     */
    private String state;
    
    /**
     * 描述
     */
    private String description;
    
    /**
     * 特征列表
     */
    private java.util.List<String> features;
    
    /**
     * 最佳季节
     */
    private String bestSeason;
    
    /**
     * 预算等级 (1=Budget, 2=Moderate, 3=Luxury)
     */
    private Integer budgetLevel;
    
    /**
     * 推荐天数 (3-5 天)
     */
    private Integer recommendedDays;
    
    /**
     * 预估费用（基于推荐天数）
     */
    private Integer estimatedCost;
    
    /**
     * 匹配分数 (0-100)
     */
    private Integer matchScore;
    
    /**
     * 推荐理由
     */
    private String recommendReason;
    
    /**
     * 纬度
     */
    private Double latitude;
    
    /**
     * 经度
     */
    private Double longitude;
}
