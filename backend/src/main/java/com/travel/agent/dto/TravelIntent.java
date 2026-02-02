package com.travel.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 旅行意图分析结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TravelIntent {
    
    /**
     * 意图类型
     */
    private IntentType type;
    
    /**
     * 目的地名称（如果明确）
     */
    private String destination;
    
    /**
     * 天数
     */
    private Integer days;
    
    /**
     * 预算（字符串格式，如"1万"、"5000-10000"）
     */
    private String budget;
    
    /**
     * 兴趣偏好
     */
    private List<String> interests;
    
    /**
     * 心情/旅行风格
     */
    private String mood;
    
    /**
     * 置信度 (0-1)
     */
    private Double confidence;
    
    /**
     * 是否需要推荐
     */
    private Boolean needsRecommendation;
    
    /**
     * 是否可以直接生成行程
     */
    private Boolean readyForItinerary;
    
    /**
     * 意图类型枚举
     */
    public enum IntentType {
        /**
         * 目的地明确（如：我想去北京玩7天）
         */
        DESTINATION_CLEAR,
        
        /**
         * 目的地不明确（如：我想去海边度假）
         */
        DESTINATION_UNCLEAR,
        
        /**
         * 一般聊天（如：你好、谢谢）
         */
        GENERAL_CHAT,
        
        /**
         * 继续对话（多轮对话中的后续消息）
         */
        CONTINUE_CONVERSATION
    }
}
