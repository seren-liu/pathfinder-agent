package com.travel.agent.service.impl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.travel.agent.config.OpenAIConfig;
import com.travel.agent.dto.AIDestinationRecommendation;
import com.travel.agent.dto.AIRecommendationResponse;
import com.travel.agent.dto.response.ParseIntentResponse;
import com.travel.agent.entity.UserPreferences;
import com.travel.agent.exception.BusinessException;
import com.travel.agent.service.AIService;
import com.travel.agent.service.UserPreferencesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIServiceImpl implements AIService {

    private final OpenAIConfig openAIConfig;
    private final com.travel.agent.config.AIProviderConfig aiProviderConfig;  // 新增
    private final com.travel.agent.service.GeminiService geminiService;  // 新增
    private final UserPreferencesService userPreferencesService;
    private final Gson gson = new Gson();
    
    private OkHttpClient client;

    /**
     * 初始化 HTTP 客户端
     */
    private OkHttpClient getClient() {
        if (client == null) {
            client = new OkHttpClient.Builder()
                    .connectTimeout(openAIConfig.getTimeout(), TimeUnit.MILLISECONDS)
                    .readTimeout(openAIConfig.getTimeout(), TimeUnit.MILLISECONDS)
                    .writeTimeout(openAIConfig.getTimeout(), TimeUnit.MILLISECONDS)
                    .build();
        }
        return client;
    }

    @Override
    public ParseIntentResponse parseIntent(String userInput, Long userId) {
        log.info("Parsing intent for user: {}, input: {}", userId, userInput);

        // 1. 获取用户偏好
        UserPreferences preferences = userPreferencesService.findByUserId(userId);
        if (preferences == null) {
            throw new BusinessException("User preferences not found. Please complete profile first.");
        }

        // 2. 解析用户兴趣
        List<String> interests = userPreferencesService.parseInterests(preferences.getInterests());

        // 3. 构建 Prompt
        String prompt = buildIntentPrompt(userInput, preferences.getTravelStyle(), interests, preferences.getBudgetPreference());

        // 4. 调用 AI API（支持 Gemini/OpenAI）
        String aiResponse = chat(prompt);

        // 5. 清理并解析 JSON
        String cleanedJson = cleanJsonResponse(aiResponse);
        
        try {
            ParseIntentResponse response = gson.fromJson(cleanedJson, ParseIntentResponse.class);
            
            // 6. 生成会话ID
            response.setSessionId(UUID.randomUUID().toString());
            
            log.info("Intent parsed successfully: {}", response);
            return response;
            
        } catch (Exception e) {
            log.error("Failed to parse AI response: {}", aiResponse, e);
            throw new BusinessException("AI returned invalid response. Please try again.");
        }
    }

    @Override
    public String generateRecommendReason(
            String destinationName,
            String destinationDescription,
            List<String> destinationFeatures,
            String userMood,
            List<String> userInterests,
            String travelStyle
    ) {
        log.info("Generating recommendation reason for: {}", destinationName);

        String prompt = buildRecommendPrompt(
                destinationName,
                destinationDescription,
                destinationFeatures,
                userMood,
                userInterests,
                travelStyle
        );

        String aiResponse = chat(prompt);
        
        // 清理响应（移除多余的引号或换行）
        return aiResponse.trim()
                .replaceAll("^\"|\"$", "")
                .replaceAll("\\n+", " ");
    }

    /**
     * 构建意图解析 Prompt
     */
    private String buildIntentPrompt(String userInput, String travelStyle, List<String> interests, Byte budgetPreference) {
        return String.format("""
            User input: "%s"
            
            User profile:
            - Travel style: %s
            - Interests: %s
            - Budget preference: %s (1=budget, 2=moderate, 3=luxury)
            
            Analyze and extract structured information from the user's input.
            Consider their profile when determining preferences.
            
            Return ONLY valid JSON (no markdown, no code blocks):
            {
              "mood": "relaxed|adventure|romantic|business|cultural",
              "keywords": ["beach", "mountains", "nature"],
              "budgetLevel": 2,
              "preferredFeatures": ["beach", "nature", "relaxation"],
              "estimatedDuration": 5,
              "followUpQuestions": ["Are you looking for domestic or international?", "What time of year?"]
            }
            
            Guidelines:
            - mood: Infer from emotional context in their input
            - keywords: Extract 2-5 key travel-related words
            - budgetLevel: MUST be a single integer (1, 2, or 3). Use their profile's budget_preference unless they specify differently
            - preferredFeatures: Match to standard features (beach, mountains, culture, food, nature, urban, relaxation, adventure, wildlife)
            - estimatedDuration: MUST be a single integer (3-7 days). Infer from "weekend"=2, "few days"=3, "week"=7, etc.
            - followUpQuestions: 1-2 relevant clarifying questions
            
            CRITICAL: budgetLevel and estimatedDuration MUST be integers, NOT ranges or strings
            
            Important: Return ONLY the JSON object, no additional text.
            """,
            userInput,
            travelStyle,
            String.join(", ", interests),
            budgetPreference
        );
    }

    /**
     * 构建推荐理由 Prompt
     */
    private String buildRecommendPrompt(
            String destinationName,
            String destinationDescription,
            List<String> destinationFeatures,
            String userMood,
            List<String> userInterests,
            String travelStyle
    ) {
        return String.format("""
            Generate 2-3 compelling sentences recommending %s for a traveler.
            
            Destination info:
            - Name: %s
            - Description: %s
            - Features: %s
            
            Traveler profile:
            - Mood: %s
            - Interests: %s
            - Travel style: %s
            
            Requirements:
            - Tone: Warm, encouraging, authentic
            - Length: 60-100 words (2-3 sentences)
            - Be SPECIFIC about this destination (use actual place names, activities)
            - Connect to their mood and interests
            - NO generic phrases like "great destination" or "many things to do"
            - Focus on unique aspects that match their preferences
            
            Example GOOD response:
            "Perfect for your healing journey with pristine beaches and lush hinterland. The area offers peaceful coastal walks and beautiful sunrise spots at Cape Byron Lighthouse. You'll find plenty of quiet corners to recharge away from crowds."
            
            Example BAD response:
            "This is a great destination with many things to do. You'll love it here. There are beaches and activities for everyone."
            
            Return ONLY the recommendation text (no quotes, no extra formatting).
            """,
            destinationName,
            destinationName,
            destinationDescription,
            String.join(", ", destinationFeatures),
            userMood,
            String.join(", ", userInterests),
            travelStyle
        );
    }

    /**
     * 调用 OpenAI API
     */
    private String callOpenAI(String prompt) {
        try {
            // 构建请求 Body
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", openAIConfig.getModel());
            addTokenLimitParameter(requestBody);
            addTemperatureParameter(requestBody, openAIConfig.getTemperature());
            
            // 添加消息
            JsonObject message = new JsonObject();
            message.addProperty("role", "user");
            message.addProperty("content", prompt);
            
            requestBody.add("messages", gson.toJsonTree(Arrays.asList(message)));

            // 构建 HTTP 请求
            Request request = new Request.Builder()
                    .url(openAIConfig.getBaseUrl() + "/chat/completions")
                    .header("Authorization", "Bearer " + openAIConfig.getApiKey())
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(
                            requestBody.toString(),
                            MediaType.parse("application/json")
                    ))
                    .build();

            // 执行请求
            try (Response response = getClient().newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "unknown";
                    log.error("OpenAI API error: status={}, body={}", response.code(), errorBody);
                    throw new BusinessException("AI service unavailable. Please try again later.");
                }

                String responseBody = response.body().string();
                JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                
                // 提取 AI 回复
                return jsonResponse
                        .getAsJsonArray("choices")
                        .get(0)
                        .getAsJsonObject()
                        .getAsJsonObject("message")
                        .get("content")
                        .getAsString();
            }

        } catch (IOException e) {
            log.error("Failed to call OpenAI API", e);
            throw new BusinessException("Failed to process AI request. Please try again.");
        }
    }

    /**
     * GPT-5 系列使用 max_completion_tokens，旧模型保持 max_tokens 兼容。
     */
    private void addTokenLimitParameter(JsonObject requestBody) {
        String model = openAIConfig.getModel();
        Integer maxTokens = openAIConfig.getMaxTokens();
        if (maxTokens == null) {
            return;
        }
        if (model != null && model.toLowerCase().startsWith("gpt-5")) {
            requestBody.addProperty("max_completion_tokens", maxTokens);
        } else {
            requestBody.addProperty("max_tokens", maxTokens);
        }
    }

    /**
     * GPT-5 系列当前仅支持默认 temperature，避免显式传参触发 400。
     */
    private void addTemperatureParameter(JsonObject requestBody, Double temperature) {
        if (temperature == null) {
            return;
        }
        String model = openAIConfig.getModel();
        if (model != null && model.toLowerCase().startsWith("gpt-5")) {
            return;
        }
        requestBody.addProperty("temperature", temperature);
    }

    /**
     * 清理 JSON 响应（移除 markdown 代码块和修复常见问题）
     */
    private String cleanJsonResponse(String response) {
        String cleaned = response
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();
        
        // 修复常见的 JSON 问题
        // 1. 移除对象/数组最后一个元素后的尾随逗号
        cleaned = cleaned.replaceAll(",\\s*}", "}");
        cleaned = cleaned.replaceAll(",\\s*]", "]");
        
        // 2. 移除注释（单行和多行）
        cleaned = cleaned.replaceAll("//.*", "");
        cleaned = cleaned.replaceAll("/\\*.*?\\*/", "");
        
        return cleaned.trim();
    }

    @Override
    public List<AIDestinationRecommendation> generateDestinationRecommendations(
            ParseIntentResponse parsedIntent,
            UserPreferences userPreferences,
            List<String> excludeDestinationNames
    ) {
        log.info("Using AI to generate destination recommendations based on user intent");
        if (excludeDestinationNames != null && !excludeDestinationNames.isEmpty()) {
            log.info("🚫 Excluding destinations: {}", excludeDestinationNames);
        }
        
        // 1. 获取用户兴趣
        List<String> userInterests = userPreferencesService.parseInterests(userPreferences.getInterests());
        
        // 2. 构建 Prompt（让 AI 直接生成推荐，不依赖数据库）
        String prompt = buildDirectRecommendationPrompt(parsedIntent, userPreferences, userInterests, excludeDestinationNames);
        
        // 3. 调用 AI API（支持 Gemini/OpenAI）
        String aiResponse = chat(prompt);
        
        // 4. 解析 JSON 响应
        try {
            String cleanedJson = cleanJsonResponse(aiResponse);
            log.info("🔍 Cleaned JSON (first 1000 chars): {}", cleanedJson.substring(0, Math.min(1000, cleanedJson.length())));
            
            AIRecommendationResponse response = gson.fromJson(cleanedJson, AIRecommendationResponse.class);
            
            if (response == null || response.getRecommendations() == null || response.getRecommendations().isEmpty()) {
                log.warn("AI returned empty recommendations");
                throw new BusinessException("Failed to generate recommendations. Please try again.");
            }
            
            log.info("AI generated {} destination recommendations", response.getRecommendations().size());
            
            // 调试：打印每个推荐的 recommendedDays
            for (AIDestinationRecommendation rec : response.getRecommendations()) {
                log.info("🔍 Destination: {}, recommendedDays: {}, estimatedCost: {}", 
                    rec.getDestinationName(), rec.getRecommendedDays(), rec.getEstimatedCost());
            }
            
            return response.getRecommendations();
            
        } catch (com.google.gson.JsonSyntaxException e) {
            log.error("❌ JSON parsing error. Raw response: {}", aiResponse);
            log.error("❌ Cleaned JSON: {}", cleanJsonResponse(aiResponse));
            log.error("❌ Error details: ", e);
            throw new BusinessException("AI returned invalid JSON format. Please try again.");
        } catch (Exception e) {
            log.error("Failed to parse AI recommendation response: {}", aiResponse, e);
            throw new BusinessException("Failed to generate recommendations. Please try again.");
        }
    }

    /**
     * 构建直接推荐 Prompt（AI 直接生成目的地，不依赖数据库）
     */
    private String buildDirectRecommendationPrompt(
            ParseIntentResponse parsedIntent,
            UserPreferences userPreferences,
            List<String> userInterests,
            List<String> excludeDestinationNames
    ) {
        String excludeSection = "";
        if (excludeDestinationNames != null && !excludeDestinationNames.isEmpty()) {
            excludeSection = String.format("""
                
                🚫 EXCLUDED DESTINATIONS (DO NOT recommend these):
                %s
                
                IMPORTANT: You MUST recommend 3 DIFFERENT destinations that are NOT in the excluded list above.
                """, String.join(", ", excludeDestinationNames));
        }
        
        // 构建目的地约束
        String destinationConstraint = "";
        if (parsedIntent.getDestination() != null && !parsedIntent.getDestination().isEmpty()) {
            destinationConstraint = String.format("""
                
                🎯 CRITICAL DESTINATION REQUIREMENT:
                User specifically wants destinations in/related to: "%s"
                You MUST recommend destinations that match this region/preference.
                For example:
                - "南美洲" or "South America" → recommend destinations in South America (Brazil, Peru, Argentina, etc.)
                - "欧洲" or "Europe" → recommend destinations in Europe (Paris, Rome, Barcelona, etc.)
                - "海滩" or "beach" → recommend beach destinations worldwide
                - "亚洲" or "Asia" → recommend destinations in Asia (Thailand, Japan, Vietnam, etc.)
                
                DO NOT recommend destinations outside this specified region/preference!
                """, parsedIntent.getDestination());
        }
        
        return String.format("""
            Recommend 3 destinations. Prioritize intent (80%%) over preferences (20%%).
            %s%s
            Intent: %s mood, %s, %s, %s budget, %d days
            Preferences: %s, %s
            
            Return JSON with: destinationName, country, state, description (2-3 sentences), features (3-5), bestSeason, budgetLevel (1-3), recommendedDays (3-5, vary each), estimatedCost, matchScore (0-100), recommendReason (intent first, then preferences), latitude, longitude
            
            EXAMPLE for intent "November island country in South Pacific":
            {
              "recommendations": [
                {
                  "destinationId": null,
                  "destinationName": "Moorea",
                  "country": "French Polynesia",
                  "state": null,
                  "description": "A stunning South Pacific island paradise with dramatic volcanic peaks, crystal-clear lagoons, and pristine beaches. Moorea offers the perfect blend of natural beauty and outdoor adventure.",
                  "features": ["beach", "island", "nature", "hiking", "water-sports"],
                  "bestSeason": "May-Oct",
                  "budgetLevel": 3,
                  "recommendedDays": 4,
                  "estimatedCost": 3500,
                  "matchScore": 95,
                  "recommendReason": "Moorea is a perfect South Pacific island destination for November, offering ideal weather conditions and stunning natural landscapes. As an island country in the region you specified, it provides excellent hiking trails through lush valleys and along coastal paths, matching your adventurous spirit and solo travel style.",
                  "latitude": -17.5334,
                  "longitude": -149.8297
                }
              ]
            }
            
            Return valid JSON only (no markdown). No trailing commas. Vary recommendedDays: beach=3, city=4, nature=5.
            """,
            excludeSection,
            destinationConstraint,
            parsedIntent.getMood() != null ? parsedIntent.getMood() : "relaxing",
            parsedIntent.getKeywords() != null ? String.join(", ", parsedIntent.getKeywords()) : "travel",
            parsedIntent.getPreferredFeatures() != null ? String.join(", ", parsedIntent.getPreferredFeatures()) : "sightseeing",
            getBudgetLabel(parsedIntent.getBudgetLevel()),
            parsedIntent.getEstimatedDuration() != null ? parsedIntent.getEstimatedDuration() : 5,
            userInterests != null && !userInterests.isEmpty() ? String.join(", ", userInterests) : "general travel",
            userPreferences.getTravelStyle() != null ? userPreferences.getTravelStyle() : "balanced"
        );
    }

    /**
     * 获取预算等级标签
     */
    private String getBudgetLabel(Number budgetLevel) {
        if (budgetLevel == null) return "Moderate";
        int level = budgetLevel.intValue();
        return switch (level) {
            case 1 -> "Budget";
            case 2 -> "Moderate";
            case 3 -> "Luxury";
            default -> "Moderate";
        };
    }
    
    /**
     * 通用 AI 对话方法（支持多提供商，带自动降级）
     */
    @Override
    public String chat(String prompt) {
        String primaryProvider = aiProviderConfig.getPrimaryProvider();
        boolean enableFallback = aiProviderConfig.getEnableFallback();
        
        log.info("🤖 AI chat request: primary={}, fallback={}, prompt_length={}", 
            primaryProvider, enableFallback, prompt.length());
        
        try {
            // 尝试主要提供商
            if ("gemini".equalsIgnoreCase(primaryProvider)) {
                log.info("📍 Using Gemini as primary provider");
                return geminiService.chat(prompt);
            } else {
                log.info("📍 Using OpenAI as primary provider");
                return callOpenAI(prompt);
            }
            
        } catch (Exception primaryError) {
            log.warn("⚠️ Primary AI provider ({}) failed: {}", primaryProvider, primaryError.getMessage());
            
            // 如果启用了降级，尝试备用提供商
            if (enableFallback) {
                String fallbackProvider = aiProviderConfig.getFallbackProvider();
                log.info("🔄 Falling back to {}", fallbackProvider);
                
                try {
                    if ("gemini".equalsIgnoreCase(fallbackProvider)) {
                        return geminiService.chat(prompt);
                    } else {
                        return callOpenAI(prompt);
                    }
                } catch (Exception fallbackError) {
                    log.error("❌ Fallback AI provider ({}) also failed: {}", 
                        fallbackProvider, fallbackError.getMessage());
                    throw new BusinessException("All AI providers failed. Please try again later.");
                }
            } else {
                // 不启用降级，直接抛出异常
                throw primaryError;
            }
        }
    }

    @Override
    public String chatWithFunctionCall(
            String prompt,
            String functionName,
            String functionDescription,
            String parametersJsonSchema
    ) {
        if (prompt == null || prompt.isBlank()) {
            throw new BusinessException("Prompt cannot be empty.");
        }
        if (functionName == null || functionName.isBlank()) {
            throw new BusinessException("Function name is required.");
        }
        if (parametersJsonSchema == null || parametersJsonSchema.isBlank()) {
            throw new BusinessException("Function parameters schema is required.");
        }

        long start = System.currentTimeMillis();
        try {
            String result = callOpenAIWithFunctionCall(
                    prompt,
                    functionName,
                    functionDescription == null ? "" : functionDescription,
                    parametersJsonSchema
            );
            log.info("⏱️ Function-call extraction finished: function={}, duration={}ms, prompt_length={}",
                    functionName, System.currentTimeMillis() - start, prompt.length());
            return result;
        } catch (Exception e) {
            log.error("❌ OpenAI function call failed: function={}", functionName, e);
            throw new BusinessException("Failed to execute structured AI extraction. Please try again.");
        }
    }

    private String callOpenAIWithFunctionCall(
            String prompt,
            String functionName,
            String functionDescription,
            String parametersJsonSchema
    ) {
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", openAIConfig.getModel());
            addTokenLimitParameter(requestBody);
            addTemperatureParameter(requestBody, 0.0);
            requestBody.addProperty("parallel_tool_calls", false);

            JsonObject message = new JsonObject();
            message.addProperty("role", "user");
            message.addProperty("content", prompt);
            requestBody.add("messages", gson.toJsonTree(List.of(message)));

            JsonObject function = new JsonObject();
            function.addProperty("name", functionName);
            function.addProperty("description", functionDescription);
            function.add("parameters", JsonParser.parseString(parametersJsonSchema));

            JsonObject tool = new JsonObject();
            tool.addProperty("type", "function");
            tool.add("function", function);
            requestBody.add("tools", gson.toJsonTree(List.of(tool)));

            JsonObject functionChoice = new JsonObject();
            functionChoice.addProperty("name", functionName);
            JsonObject toolChoice = new JsonObject();
            toolChoice.addProperty("type", "function");
            toolChoice.add("function", functionChoice);
            requestBody.add("tool_choice", toolChoice);

            Request request = new Request.Builder()
                    .url(openAIConfig.getBaseUrl() + "/chat/completions")
                    .header("Authorization", "Bearer " + openAIConfig.getApiKey())
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(
                            requestBody.toString(),
                            MediaType.parse("application/json")
                    ))
                    .build();

            try (Response response = getClient().newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "unknown";
                    log.error("OpenAI function call API error: status={}, body={}", response.code(), errorBody);
                    throw new BusinessException("Structured AI service unavailable.");
                }

                if (response.body() == null) {
                    throw new BusinessException("Structured AI returned empty response.");
                }

                String responseBody = response.body().string();
                JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                JsonObject messageObj = jsonResponse
                        .getAsJsonArray("choices")
                        .get(0)
                        .getAsJsonObject()
                        .getAsJsonObject("message");

                JsonArray toolCalls = messageObj.getAsJsonArray("tool_calls");
                if (toolCalls == null || toolCalls.isEmpty()) {
                    throw new BusinessException("Structured AI did not return tool calls.");
                }

                JsonObject firstToolCall = toolCalls.get(0).getAsJsonObject();
                JsonObject functionObj = firstToolCall.getAsJsonObject("function");
                if (functionObj == null) {
                    throw new BusinessException("Structured AI returned invalid tool call.");
                }

                JsonElement nameElement = functionObj.get("name");
                if (nameElement == null || !functionName.equals(nameElement.getAsString())) {
                    throw new BusinessException("Structured AI returned unexpected function.");
                }

                JsonElement argumentsElement = functionObj.get("arguments");
                if (argumentsElement == null || argumentsElement.getAsString().isBlank()) {
                    throw new BusinessException("Structured AI returned empty arguments.");
                }

                return cleanJsonResponse(argumentsElement.getAsString());
            }
        } catch (IOException e) {
            log.error("Failed to call OpenAI function call API", e);
            throw new BusinessException("Failed to process structured AI request.");
        }
    }
}
