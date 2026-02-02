package com.travel.agent.controller;

import com.travel.agent.dto.request.ChatRequest;
import com.travel.agent.dto.response.ChatResponse;
import com.travel.agent.dto.response.CommonResponse;
import com.travel.agent.entity.ConversationHistory;
import com.travel.agent.service.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 多轮对话API
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "Multi-turn conversation API")
public class ChatController {

    private final ConversationService conversationService;

    @PostMapping
    @Operation(summary = "Send message", description = "Send a message and get AI response")
    public CommonResponse<ChatResponse> chat(@RequestBody ChatRequest request) {
        try {
            ChatResponse response = conversationService.chat(
                request.getUserId(),
                request.getSessionId(),
                request.getMessage()
            );
            return CommonResponse.success(response);
        } catch (Exception e) {
            log.error("Chat failed", e);
            return CommonResponse.error("Chat failed: " + e.getMessage());
        }
    }

    @GetMapping("/history/{sessionId}")
    @Operation(summary = "Get conversation history")
    public CommonResponse<List<ConversationHistory>> getHistory(
            @PathVariable String sessionId,
            @RequestParam Long userId) {
        try {
            List<ConversationHistory> history = conversationService.getHistory(userId, sessionId);
            return CommonResponse.success(history);
        } catch (Exception e) {
            log.error("Get history failed", e);
            return CommonResponse.error("Get history failed: " + e.getMessage());
        }
    }

    @DeleteMapping("/{sessionId}")
    @Operation(summary = "Clear conversation history")
    public CommonResponse<Void> clearHistory(
            @PathVariable String sessionId,
            @RequestParam Long userId) {
        try {
            conversationService.clearHistory(userId, sessionId);
            return CommonResponse.success(null);
        } catch (Exception e) {
            log.error("Clear history failed", e);
            return CommonResponse.error("Clear history failed: " + e.getMessage());
        }
    }
}
