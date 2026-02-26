package com.travel.agent.controller;

import com.travel.agent.dto.request.ParseIntentRequest;
import com.travel.agent.dto.request.RecommendDestinationRequest;
import com.travel.agent.dto.response.CommonResponse;
import com.travel.agent.dto.response.DestinationResponse;
import com.travel.agent.dto.response.ParseIntentResponse;
import com.travel.agent.entity.ConversationHistory;
import com.travel.agent.service.AIService;
import com.travel.agent.service.ConversationHistoryService;
import com.travel.agent.service.DestinationsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 相关接口
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Tag(name = "AI", description = "AI 意图解析和推荐")
public class AIController {

    private final AIService aiService;
    private final ConversationHistoryService conversationHistoryService;
    private final DestinationsService destinationsService;

    /**
     * 解析用户旅行意图
     */
    @PostMapping("/parse-intent")
    @Operation(summary = "解析旅行意图", description = "使用 AI 解析用户的自然语言输入")
    public CommonResponse<ParseIntentResponse> parseIntent(@Valid @RequestBody ParseIntentRequest request) {
        log.info("Parse intent request: userId={}, input={}", request.getUserId(), request.getUserInput());

        // 1. 调用 AI 解析意图
        ParseIntentResponse response = aiService.parseIntent(request.getUserInput(), request.getUserId());

        // 2. 保存对话历史 - 用户输入
        ConversationHistory userMessage = new ConversationHistory();
        userMessage.setUserId(request.getUserId());
        userMessage.setSessionId(response.getSessionId());
        userMessage.setRole("user");
        userMessage.setMessage(request.getUserInput());
        userMessage.setCreatedAt(LocalDateTime.now());
        conversationHistoryService.save(userMessage);

        // 3. 保存对话历史 - AI 响应
        ConversationHistory aiMessage = new ConversationHistory();
        aiMessage.setUserId(request.getUserId());
        aiMessage.setSessionId(response.getSessionId());
        aiMessage.setRole("assistant");
        aiMessage.setMessage(cn.hutool.json.JSONUtil.toJsonStr(response));
        aiMessage.setCreatedAt(LocalDateTime.now());
        conversationHistoryService.save(aiMessage);

        log.info("Intent parsed successfully: sessionId={}", response.getSessionId());
        return CommonResponse.success(response);
    }

    /**
     * 推荐目的地
     */
    @PostMapping("/destinations/recommend")
    @Operation(summary = "推荐目的地", description = "根据解析的意图推荐目的地")
    public CommonResponse<List<DestinationResponse>> recommendDestinations(
            @Valid @RequestBody RecommendDestinationRequest request
    ) {
        log.info("Recommend destinations: userId={}, excludeIds={}, excludeNames={}, forceRefresh={}", 
                request.getUserId(), request.getExcludeIds(), request.getExcludeNames(), request.getForceRefresh());

        // 如果 parsedIntent 为 null，从单独的字段构建
        ParseIntentResponse parsedIntent = request.getParsedIntent();
        if (parsedIntent == null) {
            log.info("Building ParseIntentResponse from individual fields");
            parsedIntent = new ParseIntentResponse();
            parsedIntent.setSessionId(request.getSessionId());
            
            // 设置目的地偏好
            parsedIntent.setDestination(request.getDestination());
            
            // 设置 keywords（从 interests）
            List<String> keywords = request.getInterests();
            parsedIntent.setKeywords(keywords != null ? keywords : new java.util.ArrayList<>());
            
            // 设置 preferredFeatures（与 keywords 相同）
            parsedIntent.setPreferredFeatures(keywords != null ? keywords : new java.util.ArrayList<>());
            
            parsedIntent.setMood(request.getMood());
            
            // 将预算字符串转换为等级
            if (request.getBudget() != null) {
                parsedIntent.setBudgetLevel(parseBudgetLevel(request.getBudget()));
            } else {
                parsedIntent.setBudgetLevel(2); // 默认中等预算
            }
            
            parsedIntent.setEstimatedDuration(request.getDays() != null ? request.getDays() : 5);  // 默认5天
        }

        List<DestinationResponse> destinations = destinationsService.recommendDestinations(
                request.getUserId(),
                parsedIntent,
                request.getExcludeIds(),
                request.getExcludeNames(),
                request.getForceRefresh()
        );

        if (destinations.isEmpty()) {
            return CommonResponse.error(404, "No destinations found matching your preferences. Try adjusting your budget or interests.");
        }

        log.info("Returning {} destinations", destinations.size());
        return CommonResponse.success(destinations);
    }

    /**
     * 换一批目的地
     */
    @PostMapping("/destinations/recommend/next-batch")
    @Operation(summary = "换一批目的地", description = "获取下一批推荐")
    public CommonResponse<List<DestinationResponse>> nextBatch(
            @Valid @RequestBody RecommendDestinationRequest request
    ) {
        log.info("Next batch request: userId={}, excludeIds={}", 
                request.getUserId(), request.getExcludeIds());

        return recommendDestinations(request);
    }

    private int parseBudgetLevel(String budget) {
        if (budget == null || budget.isBlank()) {
            return 2;
        }
        try {
            String digits = budget.replaceAll("[^0-9]", "");
            if (digits.isBlank()) {
                return 2;
            }
            int budgetValue = Integer.parseInt(digits);
            if (budgetValue < 5000) {
                return 1;
            }
            if (budgetValue < 15000) {
                return 2;
            }
            return 3;
        } catch (Exception e) {
            return 2;
        }
    }
}
