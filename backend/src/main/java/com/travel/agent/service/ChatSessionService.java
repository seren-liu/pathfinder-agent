package com.travel.agent.service;

import com.travel.agent.dto.response.ChatSessionMessageResponse;
import com.travel.agent.dto.response.ChatSessionSummaryResponse;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatSessionService {

    ChatSessionSummaryResponse ensureSession(Long userId, String sessionId, String title);

    List<ChatSessionSummaryResponse> listSessions(Long userId, Integer limit);

    List<ChatSessionMessageResponse> listMessages(Long userId, String sessionId, Integer limit);

    void appendMessage(Long userId, String sessionId, String role, String content, LocalDateTime createdAt);

    void deleteSession(Long userId, String sessionId);
}

