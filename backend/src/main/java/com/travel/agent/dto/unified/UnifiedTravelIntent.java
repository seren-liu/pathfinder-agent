package com.travel.agent.dto.unified;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一旅行意图模型
 * 
 * 设计原则：
 * 1. Single Source of Truth - 所有组件使用同一个模型
 * 2. Type Safety - 使用枚举代替字符串
 * 3. Immutability - 使用 @Builder 创建不可变对象
 * 4. Validation - 使用 JSR-303 验证
 * 5. Serializable - 支持 Redis 缓存和 LangGraph 序列化
 * 
 * 替代：
 * - TravelIntent
 * - ParseIntentResponse
 * - AgentState 中的部分字段
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "统一旅行意图模型")
public class UnifiedTravelIntent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    // ========== 会话信息 ==========
    
    @NotNull(message = "Session ID cannot be null")
    @Schema(description = "会话ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String sessionId;
    
    @NotNull(message = "User ID cannot be null")
    @Schema(description = "用户ID", example = "1")
    private Long userId;
    
    // ========== 目的地信息 ==========
    
    @Schema(description = "目的地名称", example = "Paris")
    private String destination;
    
    @Schema(description = "目的地类型")
    @Builder.Default
    private DestinationType destinationType = DestinationType.UNKNOWN;
    
    @Schema(description = "目的地国家", example = "France")
    private String destinationCountry;
    
    @Schema(description = "目的地纬度", example = "48.8566")
    private Double destinationLatitude;
    
    @Schema(description = "目的地经度", example = "2.3522")
    private Double destinationLongitude;
    
    // ========== 旅行参数 ==========
    
    @Min(value = 1, message = "Days must be at least 1")
    @Max(value = 365, message = "Days cannot exceed 365")
    @Schema(description = "旅行天数", example = "7")
    private Integer days;
    
    @Schema(description = "预算金额", example = "10000.00")
    private BigDecimal budget;
    
    @Min(value = 1, message = "Budget level must be 1-3")
    @Max(value = 3, message = "Budget level must be 1-3")
    @Schema(description = "预算等级 (1=低, 2=中, 3=高)", example = "2")
    @Builder.Default
    private Integer budgetLevel = 2;
    
    @Min(value = 1, message = "Party size must be at least 1")
    @Schema(description = "出行人数", example = "2")
    @Builder.Default
    private Integer partySize = 2;
    
    // ========== 偏好信息 ==========
    
    @Schema(description = "兴趣列表", example = "[\"beach\", \"culture\", \"food\"]")
    @Builder.Default
    private List<String> interests = new ArrayList<>();
    
    @Schema(description = "心情/旅行风格", example = "relaxing")
    private String mood;
    
    @Schema(description = "旅行风格")
    @Builder.Default
    private TravelStyle travelStyle = TravelStyle.BALANCED;
    
    // ========== 决策标记 ==========
    
    @Schema(description = "意图类型")
    @Builder.Default
    private IntentType intentType = IntentType.UNKNOWN;
    
    @Schema(description = "是否需要推荐目的地")
    @Builder.Default
    private Boolean needsRecommendation = false;
    
    @Schema(description = "是否准备好生成行程")
    @Builder.Default
    private Boolean readyForItinerary = false;
    
    @Schema(description = "置信度 (0.0-1.0)", example = "0.85")
    @Builder.Default
    private Double confidence = 0.0;
    
    // ========== 枚举定义 ==========
    
    /**
     * 目的地类型
     */
    public enum DestinationType {
        /** 具体城市 (如: Paris, Tokyo) */
        CITY,
        /** 区域 (如: Europe, Asia) */
        REGION,
        /** 国家 (如: France, Japan) */
        COUNTRY,
        /** 模糊描述 (如: beach, mountains) */
        VAGUE,
        /** 未知 */
        UNKNOWN
    }
    
    /**
     * 意图类型
     */
    public enum IntentType {
        /** 目的地明确，可以直接生成行程 */
        DESTINATION_CLEAR,
        /** 目的地不明确，需要推荐 */
        DESTINATION_UNCLEAR,
        /** 一般聊天 */
        GENERAL_CHAT,
        /** 继续对话 */
        CONTINUE_CONVERSATION,
        /** 未知 */
        UNKNOWN
    }
    
    /**
     * 旅行风格
     */
    public enum TravelStyle {
        /** 预算型 */
        BUDGET,
        /** 舒适型 */
        COMFORT,
        /** 奢华型 */
        LUXURY,
        /** 冒险型 */
        ADVENTURE,
        /** 休闲型 */
        RELAXATION,
        /** 文化型 */
        CULTURAL,
        /** 平衡型 */
        BALANCED
    }
    
    // ========== 辅助方法 ==========
    
    /**
     * 判断是否有足够信息生成行程
     */
    public boolean hasEnoughInfoForItinerary() {
        return destination != null 
            && !destination.isEmpty()
            && destinationType == DestinationType.CITY
            && days != null 
            && days > 0
            && budget != null 
            && budget.compareTo(BigDecimal.ZERO) > 0;
    }
    
    /**
     * 判断是否需要更多信息
     */
    public boolean needsMoreInfo() {
        return destination == null 
            || destination.isEmpty()
            || days == null 
            || budget == null;
    }
    
    /**
     * 从预算金额计算预算等级
     */
    public static Integer calculateBudgetLevel(BigDecimal budget) {
        if (budget == null) return 2;
        if (budget.compareTo(new BigDecimal("5000")) < 0) return 1;
        if (budget.compareTo(new BigDecimal("15000")) < 0) return 2;
        return 3;
    }
    
    /**
     * 从预算等级估算预算金额
     */
    public static BigDecimal estimateBudget(Integer budgetLevel, Integer days) {
        if (budgetLevel == null) budgetLevel = 2;
        if (days == null) days = 5;
        
        BigDecimal dailyBudget = switch (budgetLevel) {
            case 1 -> new BigDecimal("500");   // 低预算: 500/天
            case 2 -> new BigDecimal("1500");  // 中预算: 1500/天
            case 3 -> new BigDecimal("5000");  // 高预算: 5000/天
            default -> new BigDecimal("1500");
        };
        
        return dailyBudget.multiply(new BigDecimal(days));
    }
    
    /**
     * 创建默认实例
     */
    public static UnifiedTravelIntent createDefault(Long userId, String sessionId) {
        return UnifiedTravelIntent.builder()
            .userId(userId)
            .sessionId(sessionId)
            .intentType(IntentType.UNKNOWN)
            .destinationType(DestinationType.UNKNOWN)
            .budgetLevel(2)
            .partySize(2)
            .travelStyle(TravelStyle.BALANCED)
            .interests(new ArrayList<>())
            .needsRecommendation(false)
            .readyForItinerary(false)
            .confidence(0.0)
            .build();
    }
}
