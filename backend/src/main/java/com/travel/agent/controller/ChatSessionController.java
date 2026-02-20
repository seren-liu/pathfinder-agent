package com.travel.agent.controller;

import com.travel.agent.dto.response.ChatSessionMessageResponse;
import com.travel.agent.dto.response.ChatSessionSummaryResponse;
import com.travel.agent.dto.response.CommonResponse;
import com.travel.agent.service.ChatSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/chat/sessions")
@RequiredArgsConstructor
@Tag(name = "Chat Sessions", description = "Cross-device synchronized chat sessions")
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    @GetMapping
    @Operation(summary = "List sessions")
    public CommonResponse<List<ChatSessionSummaryResponse>> listSessions(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "30") Integer limit) {
        try {
            return CommonResponse.success(chatSessionService.listSessions(userId, limit));
        } catch (Exception e) {
            log.error("List sessions failed", e);
            return CommonResponse.error("List sessions failed: " + e.getMessage());
        }
    }

    @PostMapping
    @Operation(summary = "Create or ensure session")
    public CommonResponse<ChatSessionSummaryResponse> createSession(
            @RequestParam Long userId,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String title) {
        try {
            String resolvedSessionId = (sessionId == null || sessionId.isBlank())
                    ? UUID.randomUUID().toString()
                    : sessionId;
            return CommonResponse.success(chatSessionService.ensureSession(userId, resolvedSessionId, title));
        } catch (Exception e) {
            log.error("Create session failed", e);
            return CommonResponse.error("Create session failed: " + e.getMessage());
        }
    }

    @GetMapping("/{sessionId}/messages")
    @Operation(summary = "List session messages")
    public CommonResponse<List<ChatSessionMessageResponse>> listMessages(
            @RequestParam Long userId,
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "200") Integer limit) {
        try {
            return CommonResponse.success(chatSessionService.listMessages(userId, sessionId, limit));
        } catch (Exception e) {
            log.error("List messages failed: sessionId={}", sessionId, e);
            return CommonResponse.error("List messages failed: " + e.getMessage());
        }
    }

    @DeleteMapping("/{sessionId}")
    @Operation(summary = "Delete session")
    public CommonResponse<Void> deleteSession(
            @RequestParam Long userId,
            @PathVariable String sessionId) {
        try {
            chatSessionService.deleteSession(userId, sessionId);
            return CommonResponse.success();
        } catch (Exception e) {
            log.error("Delete session failed: sessionId={}", sessionId, e);
            return CommonResponse.error("Delete session failed: " + e.getMessage());
        }
    }
}

