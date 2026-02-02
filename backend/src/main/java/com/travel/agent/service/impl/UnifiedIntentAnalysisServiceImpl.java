package com.travel.agent.service.impl;

import com.travel.agent.dto.unified.UnifiedTravelIntent;
import com.travel.agent.dto.unified.UnifiedTravelIntent.DestinationType;
import com.travel.agent.dto.unified.UnifiedTravelIntent.IntentType;
import com.travel.agent.service.AIService;
import com.travel.agent.service.UnifiedIntentAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一意图分析服务实现
 * 
 * 改进点：
 * 1. 使用 UnifiedTravelIntent
 * 2. 使用枚举判断目的地类型（不再硬编码）
 * 3. 更清晰的逻辑结构
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnifiedIntentAnalysisServiceImpl implements UnifiedIntentAnalysisService {
    
    private final AIService aiService;
    
    // 会话跟踪（简单实现，生产环境应使用 Redis）
    private final Map<String, Boolean> sessionTracker = new ConcurrentHashMap<>();
    
    @Override
    public UnifiedTravelIntent analyzeIntent(String userInput, Long userId, String sessionId) {
        log.info("🔍 Analyzing intent for user: {}, session: {}, input: {}", userId, sessionId, userInput);
        
        try {
            // 1. 构建分析 Prompt
            String prompt = buildIntentPrompt(userInput);
            
            // 2. 调用 AI 分析
            String aiResponse = aiService.chat(prompt);
            log.debug("AI response: {}", aiResponse);
            
            // 3. 解析 AI 响应
            UnifiedTravelIntent intent = parseIntentFromAI(aiResponse, userId, sessionId);
            
            // 4. 推断目的地类型
            if (intent.getDestination() != null) {
                DestinationType destType = inferDestinationType(intent.getDestination());
                intent.setDestinationType(destType);
                
                // 5. 根据目的地类型设置决策标记
                updateDecisionFlags(intent, destType);
            } else {
                // 没有目的地信息
                intent.setDestinationType(DestinationType.UNKNOWN);
                intent.setIntentType(IntentType.DESTINATION_UNCLEAR);
                intent.setNeedsRecommendation(true);
                intent.setReadyForItinerary(false);
            }
            
            log.info("✅ Intent analyzed: type={}, destination={}, destType={}, needsRec={}, readyForItin={}", 
                intent.getIntentType(), 
                intent.getDestination(),
                intent.getDestinationType(),
                intent.getNeedsRecommendation(), 
                intent.getReadyForItinerary());
            
            return intent;
            
        } catch (Exception e) {
            log.error("Failed to analyze intent", e);
            // 返回默认意图
            return UnifiedTravelIntent.createDefault(userId, sessionId);
        }
    }
    
    @Override
    public boolean isFirstMessage(String sessionId) {
        return !sessionTracker.containsKey(sessionId);
    }
    
    /**
     * 构建意图分析 Prompt
     */
    private String buildIntentPrompt(String userInput) {
        return String.format("""
            Analyze the following travel-related user input and extract structured information.
            
            User Input: "%s"
            
            Extract the following information:
            1. DESTINATION: The destination name (or "UNCLEAR" if not specific)
            2. DAYS: Number of days (or "UNKNOWN")
            3. BUDGET: Budget amount in numbers (or "UNKNOWN")
            4. INTERESTS: Comma-separated interests/preferences (or "UNKNOWN")
            5. MOOD: Travel mood/style (relaxing, adventurous, cultural, etc.) (or "UNKNOWN")
            6. CONFIDENCE: Confidence level 0.0-1.0
            
            Rules:
            - If destination is a region (Europe, Asia), continent, or vague (beach, mountains), mark as "UNCLEAR"
            - If destination is a specific city (Paris, Tokyo), use the city name
            - Extract numbers for days and budget
            - Identify travel interests from the text
            
            Return ONLY in this format:
            DESTINATION: [value]
            DAYS: [value]
            BUDGET: [value]
            INTERESTS: [value]
            MOOD: [value]
            CONFIDENCE: [value]
            """, userInput);
    }
    
    /**
     * 从 AI 响应解析意图
     */
    private UnifiedTravelIntent parseIntentFromAI(String aiResponse, Long userId, String sessionId) {
        UnifiedTravelIntent.UnifiedTravelIntentBuilder builder = UnifiedTravelIntent.builder()
            .userId(userId)
            .sessionId(sessionId);
        
        try {
            // 提取字段
            String destination = extractField(aiResponse, "DESTINATION");
            String days = extractField(aiResponse, "DAYS");
            String budget = extractField(aiResponse, "BUDGET");
            String interests = extractField(aiResponse, "INTERESTS");
            String mood = extractField(aiResponse, "MOOD");
            String confidence = extractField(aiResponse, "CONFIDENCE");
            
            // 设置目的地
            if (destination != null && 
                !destination.equalsIgnoreCase("UNCLEAR") && 
                !destination.equalsIgnoreCase("UNKNOWN")) {
                builder.destination(destination);
            }
            
            // 设置天数
            if (days != null && !days.equalsIgnoreCase("UNKNOWN")) {
                try {
                    builder.days(Integer.parseInt(days.trim()));
                } catch (NumberFormatException e) {
                    log.warn("Invalid days format: {}", days);
                }
            }
            
            // 设置预算
            if (budget != null && !budget.equalsIgnoreCase("UNKNOWN")) {
                try {
                    String budgetStr = budget.replaceAll("[^0-9]", "");
                    if (!budgetStr.isEmpty()) {
                        BigDecimal budgetAmount = new BigDecimal(budgetStr);
                        builder.budget(budgetAmount);
                        builder.budgetLevel(UnifiedTravelIntent.calculateBudgetLevel(budgetAmount));
                    }
                } catch (NumberFormatException e) {
                    log.warn("Invalid budget format: {}", budget);
                }
            }
            
            // 设置兴趣
            if (interests != null && !interests.equalsIgnoreCase("UNKNOWN")) {
                List<String> interestList = Arrays.asList(interests.split(","));
                builder.interests(interestList.stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList());
            } else {
                builder.interests(new ArrayList<>());
            }
            
            // 设置心情
            if (mood != null && !mood.equalsIgnoreCase("UNKNOWN")) {
                builder.mood(mood);
            }
            
            // 设置置信度
            if (confidence != null) {
                try {
                    builder.confidence(Double.parseDouble(confidence.trim()));
                } catch (NumberFormatException e) {
                    builder.confidence(0.5);
                }
            } else {
                builder.confidence(0.5);
            }
            
        } catch (Exception e) {
            log.error("Error parsing AI response", e);
        }
        
        return builder.build();
    }
    
    /**
     * 从 AI 响应中提取字段
     */
    private String extractField(String response, String fieldName) {
        String pattern = fieldName + ":\\s*(.+)";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher m = p.matcher(response);
        
        if (m.find()) {
            return m.group(1).trim();
        }
        
        return null;
    }
    
    /**
     * 推断目的地类型（使用枚举，不再硬编码）
     */
    private DestinationType inferDestinationType(String destination) {
        if (destination == null || destination.isEmpty()) {
            return DestinationType.UNKNOWN;
        }
        
        String lower = destination.toLowerCase();
        
        // 检查是否为大洲/区域
        if (isRegion(lower)) {
            return DestinationType.REGION;
        }
        
        // 检查是否为模糊描述
        if (isVagueDescription(lower)) {
            return DestinationType.VAGUE;
        }
        
        // 检查是否为大国家
        if (isLargeCountry(lower)) {
            return DestinationType.COUNTRY;
        }
        
        // 默认认为是城市
        return DestinationType.CITY;
    }
    
    /**
     * 检查是否为区域
     */
    private boolean isRegion(String destination) {
        String[] regions = {
            "europe", "欧洲", "asia", "亚洲", "africa", "非洲",
            "america", "美洲", "north america", "北美", "south america", "南美",
            "oceania", "大洋洲", "middle east", "中东"
        };
        
        for (String region : regions) {
            if (destination.contains(region)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 检查是否为模糊描述
     */
    private boolean isVagueDescription(String destination) {
        String[] vagueTerms = {
            "beach", "海滩", "mountain", "山", "island", "岛",
            "city", "城市", "countryside", "乡村",
            "tropical", "热带", "cold", "寒冷", "warm", "温暖",
            "somewhere", "某地", "anywhere", "任何地方"
        };
        
        for (String term : vagueTerms) {
            if (destination.contains(term)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 检查是否为大国家
     */
    private boolean isLargeCountry(String destination) {
        String[] largeCountries = {
            "china", "中国", "usa", "美国", "america",
            "russia", "俄罗斯", "canada", "加拿大",
            "australia", "澳大利亚", "brazil", "巴西",
            "india", "印度", "japan", "日本"
        };
        
        for (String country : largeCountries) {
            if (destination.equals(country) || destination.contains(country + " ")) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 根据目的地类型更新决策标记
     */
    private void updateDecisionFlags(UnifiedTravelIntent intent, DestinationType destType) {
        switch (destType) {
            case CITY:
                // 具体城市 - 可以直接生成行程
                intent.setIntentType(IntentType.DESTINATION_CLEAR);
                intent.setNeedsRecommendation(false);
                intent.setReadyForItinerary(intent.hasEnoughInfoForItinerary());
                break;
                
            case REGION:
            case COUNTRY:
            case VAGUE:
                // 区域/国家/模糊描述 - 需要推荐
                intent.setIntentType(IntentType.DESTINATION_UNCLEAR);
                intent.setNeedsRecommendation(true);
                intent.setReadyForItinerary(false);
                break;
                
            case UNKNOWN:
            default:
                // 未知 - 需要更多对话
                intent.setIntentType(IntentType.DESTINATION_UNCLEAR);
                intent.setNeedsRecommendation(true);
                intent.setReadyForItinerary(false);
                break;
        }
    }
}
