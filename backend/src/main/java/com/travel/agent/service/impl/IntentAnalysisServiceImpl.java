package com.travel.agent.service.impl;

import com.travel.agent.dto.TravelIntent;
import com.travel.agent.service.AIService;
import com.travel.agent.service.IntentAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 意图分析服务实现
 * 使用AI分析用户输入，提取旅行意图
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentAnalysisServiceImpl implements IntentAnalysisService {
    
    private final AIService aiService;
    private final StringRedisTemplate redisTemplate;
    
    private static final String INTENT_CACHE_PREFIX = "intent:";
    
    @Override
    public TravelIntent analyzeIntent(String userInput) {
        log.info("🔍 Analyzing intent: '{}'", userInput);
        
        // 构建意图分析prompt
        String prompt = buildIntentAnalysisPrompt(userInput);
        
        // 调用AI分析
        String aiResponse = aiService.chat(prompt);
        log.info("AI intent analysis response: {}", aiResponse);
        
        // 解析AI响应
        TravelIntent intent = parseIntentFromAI(aiResponse, userInput);
        
        log.info("✅ Intent analyzed: type={}, destination={}, confidence={}", 
                intent.getType(), intent.getDestination(), intent.getConfidence());
        
        return intent;
    }
    
    @Override
    public boolean isFirstMessage(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return true;
        }
        
        String key = INTENT_CACHE_PREFIX + sessionId;
        return !Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
    
    /**
     * 构建意图分析prompt
     */
    private String buildIntentAnalysisPrompt(String userInput) {
        return String.format("""
            You are a travel intent analyzer. Analyze the user's input and extract travel information.
            
            User input: "%s"
            
            Extract the following information:
            1. Destination: Is the destination clear and specific? (e.g., "Beijing", "Sydney" = CLEAR; "beach", "mountains" = UNCLEAR)
            2. Days: How many days? (extract number)
            3. Budget: Any budget mentioned?
            4. Interests: What are they interested in? (e.g., culture, food, nature, shopping)
            5. Mood: What's their travel mood? (e.g., relaxing, adventurous, romantic)
            
            Respond in this EXACT format:
            DESTINATION: [city name or "UNCLEAR"]
            DAYS: [number or "UNKNOWN"]
            BUDGET: [amount or "UNKNOWN"]
            INTERESTS: [comma-separated list or "UNKNOWN"]
            MOOD: [mood or "UNKNOWN"]
            CONFIDENCE: [0.0-1.0]
            
            Example 1:
            Input: "我想去北京玩7天"
            DESTINATION: Beijing
            DAYS: 7
            BUDGET: UNKNOWN
            INTERESTS: UNKNOWN
            MOOD: UNKNOWN
            CONFIDENCE: 0.9
            
            Example 2:
            Input: "我想找个安静的海边度假"
            DESTINATION: UNCLEAR
            DAYS: UNKNOWN
            BUDGET: UNKNOWN
            INTERESTS: beach, relaxing
            MOOD: peaceful
            CONFIDENCE: 0.8
            
            Now analyze the user input above.
            """, userInput);
    }
    
    /**
     * 从AI响应中解析意图
     */
    private TravelIntent parseIntentFromAI(String aiResponse, String originalInput) {
        TravelIntent.TravelIntentBuilder builder = TravelIntent.builder();
        
        try {
            // 解析AI响应
            String destination = extractField(aiResponse, "DESTINATION");
            String days = extractField(aiResponse, "DAYS");
            String budget = extractField(aiResponse, "BUDGET");
            String interests = extractField(aiResponse, "INTERESTS");
            String mood = extractField(aiResponse, "MOOD");
            String confidence = extractField(aiResponse, "CONFIDENCE");
            
            // 设置目的地
            boolean isDestinationClear = destination != null 
                    && !destination.equalsIgnoreCase("UNCLEAR") 
                    && !destination.equalsIgnoreCase("UNKNOWN")
                    && !isVagueDestination(destination);  // 检查是否是模糊目的地
            
            if (isDestinationClear) {
                builder.destination(destination);
                builder.type(TravelIntent.IntentType.DESTINATION_CLEAR);
                builder.readyForItinerary(true);
                builder.needsRecommendation(false);
            } else {
                // 模糊目的地也保存，但标记为需要推荐
                if (destination != null && !destination.equalsIgnoreCase("UNCLEAR") && !destination.equalsIgnoreCase("UNKNOWN")) {
                    builder.destination(destination);  // 保存模糊目的地（如"欧洲"）
                }
                builder.type(TravelIntent.IntentType.DESTINATION_UNCLEAR);
                builder.readyForItinerary(false);
                builder.needsRecommendation(true);
            }
            
            // 设置天数
            if (days != null && !days.equalsIgnoreCase("UNKNOWN")) {
                try {
                    builder.days(Integer.parseInt(days.trim()));
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse days: {}", days);
                }
            }
            
            // 设置预算（提取纯数字）
            if (budget != null && !budget.equalsIgnoreCase("UNKNOWN")) {
                String cleanedBudget = extractNumericValue(budget);
                if (cleanedBudget != null) {
                    builder.budget(cleanedBudget);
                }
            }
            
            // 设置兴趣
            if (interests != null && !interests.equalsIgnoreCase("UNKNOWN")) {
                List<String> interestList = Arrays.stream(interests.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
                builder.interests(interestList);
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
                    builder.confidence(0.7);
                }
            } else {
                builder.confidence(0.7);
            }
            
        } catch (Exception e) {
            log.error("Failed to parse intent from AI response", e);
            // 返回默认意图
            return TravelIntent.builder()
                    .type(TravelIntent.IntentType.GENERAL_CHAT)
                    .confidence(0.5)
                    .needsRecommendation(false)
                    .readyForItinerary(false)
                    .interests(new ArrayList<>())
                    .build();
        }
        
        return builder.build();
    }
    
    /**
     * 检查是否是模糊目的地（需要推荐具体城市）
     */
    private boolean isVagueDestination(String destination) {
        if (destination == null || destination.isEmpty()) {
            return true;
        }
        
        String lower = destination.toLowerCase();
        
        // 大洲/区域
        if (lower.contains("europe") || lower.contains("欧洲") || 
            lower.contains("asia") || lower.contains("亚洲") ||
            lower.contains("africa") || lower.contains("非洲") ||
            lower.contains("america") || lower.contains("美洲") ||
            lower.contains("oceania") || lower.contains("大洋洲")) {
            return true;
        }
        
        // 模糊描述
        if (lower.contains("beach") || lower.contains("海滩") ||
            lower.contains("mountain") || lower.contains("山") ||
            lower.contains("island") || lower.contains("岛") ||
            lower.contains("city") || lower.contains("城市") ||
            lower.contains("countryside") || lower.contains("乡村") ||
            lower.contains("tropical") || lower.contains("热带") ||
            lower.contains("cold") || lower.contains("寒冷") ||
            lower.contains("warm") || lower.contains("温暖") ||
            lower.contains("somewhere") || lower.contains("某地")) {
            return true;
        }
        
        // 国家级别（太大，需要推荐具体城市）
        // 注意：这里可以根据需求调整，有些小国家可能不需要推荐
        String[] largeCountries = {
            "china", "中国", "usa", "美国", "america",
            "russia", "俄罗斯", "canada", "加拿大",
            "australia", "澳大利亚", "brazil", "巴西",
            "india", "印度", "japan", "日本"
        };
        
        for (String country : largeCountries) {
            if (lower.equals(country) || lower.contains(country + " ")) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 从AI响应中提取字段
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
     * 从字符串中提取纯数字（去除货币单位等）
     */
    private String extractNumericValue(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        
        // 移除所有非数字字符（保留小数点）
        String numeric = value.replaceAll("[^0-9.]", "");
        
        if (numeric.isEmpty()) {
            return null;
        }
        
        try {
            // 验证是否为有效数字
            new java.math.BigDecimal(numeric);
            return numeric;
        } catch (NumberFormatException e) {
            log.warn("Failed to extract numeric value from: {}", value);
            return null;
        }
    }
}
