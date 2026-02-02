package com.travel.agent.dto.unified;

import com.travel.agent.dto.TravelIntent;
import com.travel.agent.dto.response.ParseIntentResponse;

import java.math.BigDecimal;
import java.util.ArrayList;

/**
 * 状态转换器 - 用于新旧状态模型之间的转换
 * 
 * 设计目的：
 * 1. 向后兼容 - 支持旧代码继续使用旧模型
 * 2. 渐进式迁移 - 新代码使用新模型
 * 3. 数据一致性 - 确保转换过程不丢失数据
 */
public class StateConverter {
    
    /**
     * TravelIntent → UnifiedTravelIntent
     */
    public static UnifiedTravelIntent fromTravelIntent(TravelIntent old, Long userId, String sessionId) {
        if (old == null) {
            return UnifiedTravelIntent.createDefault(userId, sessionId);
        }
        
        UnifiedTravelIntent.UnifiedTravelIntentBuilder builder = UnifiedTravelIntent.builder()
            .userId(userId)
            .sessionId(sessionId)
            .destination(old.getDestination())
            .days(old.getDays())
            .interests(old.getInterests() != null ? old.getInterests() : new ArrayList<>())
            .mood(old.getMood())
            .confidence(old.getConfidence())
            .needsRecommendation(old.getNeedsRecommendation())
            .readyForItinerary(old.getReadyForItinerary());
        
        // 转换预算
        if (old.getBudget() != null) {
            try {
                String budgetStr = old.getBudget().replaceAll("[^0-9]", "");
                BigDecimal budget = new BigDecimal(budgetStr);
                builder.budget(budget);
                builder.budgetLevel(UnifiedTravelIntent.calculateBudgetLevel(budget));
            } catch (NumberFormatException e) {
                builder.budgetLevel(2);
            }
        }
        
        // 转换意图类型
        if (old.getType() != null) {
            builder.intentType(convertIntentType(old.getType()));
        }
        
        // 推断目的地类型
        if (old.getDestination() != null) {
            builder.destinationType(inferDestinationType(old.getDestination()));
        }
        
        return builder.build();
    }
    
    /**
     * UnifiedTravelIntent → TravelIntent
     */
    public static TravelIntent toTravelIntent(UnifiedTravelIntent unified) {
        if (unified == null) {
            return null;
        }
        
        return TravelIntent.builder()
            .destination(unified.getDestination())
            .days(unified.getDays())
            .budget(unified.getBudget() != null ? unified.getBudget().toString() : null)
            .interests(unified.getInterests())
            .mood(unified.getMood())
            .confidence(unified.getConfidence())
            .needsRecommendation(unified.getNeedsRecommendation())
            .readyForItinerary(unified.getReadyForItinerary())
            .type(convertIntentType(unified.getIntentType()))
            .build();
    }
    
    /**
     * UnifiedTravelIntent → ParseIntentResponse
     */
    public static ParseIntentResponse toParsedIntentResponse(UnifiedTravelIntent unified) {
        if (unified == null) {
            return null;
        }
        
        ParseIntentResponse response = new ParseIntentResponse();
        response.setSessionId(unified.getSessionId());
        response.setDestination(unified.getDestination());
        response.setMood(unified.getMood());
        response.setKeywords(unified.getInterests());
        response.setPreferredFeatures(unified.getInterests());
        response.setBudgetLevel(unified.getBudgetLevel());
        response.setEstimatedDuration(unified.getDays());
        
        return response;
    }
    
    /**
     * ParseIntentResponse → UnifiedTravelIntent
     */
    public static UnifiedTravelIntent fromParsedIntentResponse(ParseIntentResponse parsed, Long userId) {
        if (parsed == null) {
            return UnifiedTravelIntent.createDefault(userId, parsed != null ? parsed.getSessionId() : null);
        }
        
        UnifiedTravelIntent.UnifiedTravelIntentBuilder builder = UnifiedTravelIntent.builder()
            .userId(userId)
            .sessionId(parsed.getSessionId())
            .destination(parsed.getDestination())
            .days(parsed.getEstimatedDuration())
            .interests(parsed.getKeywords() != null ? parsed.getKeywords() : new ArrayList<>())
            .mood(parsed.getMood())
            .budgetLevel(parsed.getBudgetLevel());
        
        // 从预算等级估算预算金额
        if (parsed.getBudgetLevel() != null && parsed.getEstimatedDuration() != null) {
            builder.budget(UnifiedTravelIntent.estimateBudget(
                parsed.getBudgetLevel(), 
                parsed.getEstimatedDuration()
            ));
        }
        
        // 推断目的地类型
        if (parsed.getDestination() != null) {
            builder.destinationType(inferDestinationType(parsed.getDestination()));
        }
        
        return builder.build();
    }
    
    // ========== 辅助方法 ==========
    
    /**
     * 转换意图类型：TravelIntent.IntentType → UnifiedTravelIntent.IntentType
     */
    private static UnifiedTravelIntent.IntentType convertIntentType(TravelIntent.IntentType old) {
        if (old == null) {
            return UnifiedTravelIntent.IntentType.UNKNOWN;
        }
        
        return switch (old) {
            case DESTINATION_CLEAR -> UnifiedTravelIntent.IntentType.DESTINATION_CLEAR;
            case DESTINATION_UNCLEAR -> UnifiedTravelIntent.IntentType.DESTINATION_UNCLEAR;
            case GENERAL_CHAT -> UnifiedTravelIntent.IntentType.GENERAL_CHAT;
            case CONTINUE_CONVERSATION -> UnifiedTravelIntent.IntentType.CONTINUE_CONVERSATION;
        };
    }
    
    /**
     * 转换意图类型：UnifiedTravelIntent.IntentType → TravelIntent.IntentType
     */
    private static TravelIntent.IntentType convertIntentType(UnifiedTravelIntent.IntentType unified) {
        if (unified == null) {
            return TravelIntent.IntentType.GENERAL_CHAT;
        }
        
        return switch (unified) {
            case DESTINATION_CLEAR -> TravelIntent.IntentType.DESTINATION_CLEAR;
            case DESTINATION_UNCLEAR -> TravelIntent.IntentType.DESTINATION_UNCLEAR;
            case GENERAL_CHAT -> TravelIntent.IntentType.GENERAL_CHAT;
            case CONTINUE_CONVERSATION -> TravelIntent.IntentType.CONTINUE_CONVERSATION;
            case UNKNOWN -> TravelIntent.IntentType.GENERAL_CHAT;
        };
    }
    
    /**
     * 推断目的地类型
     */
    private static UnifiedTravelIntent.DestinationType inferDestinationType(String destination) {
        if (destination == null || destination.isEmpty()) {
            return UnifiedTravelIntent.DestinationType.UNKNOWN;
        }
        
        String lower = destination.toLowerCase();
        
        // 大洲/区域
        if (lower.contains("europe") || lower.contains("欧洲") || 
            lower.contains("asia") || lower.contains("亚洲") ||
            lower.contains("africa") || lower.contains("非洲") ||
            lower.contains("america") || lower.contains("美洲") ||
            lower.contains("oceania") || lower.contains("大洋洲")) {
            return UnifiedTravelIntent.DestinationType.REGION;
        }
        
        // 模糊描述
        if (lower.contains("beach") || lower.contains("海滩") ||
            lower.contains("mountain") || lower.contains("山") ||
            lower.contains("island") || lower.contains("岛") ||
            lower.contains("countryside") || lower.contains("乡村") ||
            lower.contains("tropical") || lower.contains("热带")) {
            return UnifiedTravelIntent.DestinationType.VAGUE;
        }
        
        // 大国家
        String[] largeCountries = {
            "china", "中国", "usa", "美国", "america",
            "russia", "俄罗斯", "canada", "加拿大",
            "australia", "澳大利亚", "brazil", "巴西",
            "india", "印度"
        };
        
        for (String country : largeCountries) {
            if (lower.equals(country) || lower.contains(country + " ")) {
                return UnifiedTravelIntent.DestinationType.COUNTRY;
            }
        }
        
        // 默认认为是城市
        return UnifiedTravelIntent.DestinationType.CITY;
    }
}
