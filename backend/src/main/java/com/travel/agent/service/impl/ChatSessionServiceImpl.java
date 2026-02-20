package com.travel.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travel.agent.dto.response.ChatSessionMessageResponse;
import com.travel.agent.dto.response.ChatSessionSummaryResponse;
import com.travel.agent.entity.ChatSession;
import com.travel.agent.entity.ChatSessionMessage;
import com.travel.agent.mapper.ChatSessionMapper;
import com.travel.agent.mapper.ChatSessionMessageMapper;
import com.travel.agent.service.ChatSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl implements ChatSessionService {

    private static final int DEFAULT_SESSION_LIMIT = 30;
    private static final int DEFAULT_MESSAGE_LIMIT = 200;
    private static final int MAX_TITLE_LENGTH = 80;
    private static final int MAX_PREVIEW_LENGTH = 180;
    private static final String DEFAULT_TITLE = "New conversation";
    private static final long SESSION_CACHE_TTL_SECONDS = 300;

    private final ChatSessionMapper chatSessionMapper;
    private final ChatSessionMessageMapper chatSessionMessageMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatSessionSummaryResponse ensureSession(Long userId, String sessionId, String title) {
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(sessionId, "sessionId is required");

        ChatSession existing = chatSessionMapper.selectById(sessionId);
        if (existing != null) {
            if (!userId.equals(existing.getUserId())) {
                throw new IllegalArgumentException("Session does not belong to this user");
            }
            return toSummary(existing);
        }

        LocalDateTime now = LocalDateTime.now();
        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setTitle(cleanTitle(title));
        session.setLastMessage("");
        session.setMessageCount(0);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        chatSessionMapper.insert(session);
        evictSessionListCache(userId);
        return toSummary(session);
    }

    @Override
    public List<ChatSessionSummaryResponse> listSessions(Long userId, Integer limit) {
        Objects.requireNonNull(userId, "userId is required");
        int boundedLimit = (limit == null || limit <= 0) ? DEFAULT_SESSION_LIMIT : Math.min(limit, 100);

        String cacheKey = getSessionListCacheKey(userId);
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cached != null && !cached.isBlank()) {
                List<ChatSessionSummaryResponse> all = objectMapper.readValue(
                        cached,
                        new TypeReference<List<ChatSessionSummaryResponse>>() {}
                );
                return all.size() > boundedLimit ? all.subList(0, boundedLimit) : all;
            }
        } catch (Exception e) {
            log.warn("Failed to read session list cache for user {}", userId, e);
        }

        List<ChatSession> sessions = chatSessionMapper.selectList(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
                        .orderByDesc(ChatSession::getUpdatedAt)
                        .last("LIMIT 100")
        );

        List<ChatSessionSummaryResponse> allSummaries = sessions.stream()
                .map(this::toSummary)
                .collect(Collectors.toList());

        try {
            stringRedisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(allSummaries),
                    java.time.Duration.ofSeconds(SESSION_CACHE_TTL_SECONDS)
            );
        } catch (Exception e) {
            log.warn("Failed to write session list cache for user {}", userId, e);
        }

        return allSummaries.size() > boundedLimit ? allSummaries.subList(0, boundedLimit) : allSummaries;
    }

    @Override
    public List<ChatSessionMessageResponse> listMessages(Long userId, String sessionId, Integer limit) {
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(sessionId, "sessionId is required");

        validateSessionOwnership(userId, sessionId);

        int boundedLimit = (limit == null || limit <= 0) ? DEFAULT_MESSAGE_LIMIT : Math.min(limit, 1000);
        List<ChatSessionMessage> messages = chatSessionMessageMapper.selectList(
                new LambdaQueryWrapper<ChatSessionMessage>()
                        .eq(ChatSessionMessage::getUserId, userId)
                        .eq(ChatSessionMessage::getSessionId, sessionId)
                        .orderByAsc(ChatSessionMessage::getCreatedAt)
                        .last("LIMIT " + boundedLimit)
        );

        return messages.stream().map(this::toMessage).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void appendMessage(Long userId, String sessionId, String role, String content, LocalDateTime createdAt) {
        if (content == null || content.isBlank()) {
            return;
        }
        ensureSession(userId, sessionId, null);

        LocalDateTime now = createdAt == null ? LocalDateTime.now() : createdAt;
        ChatSessionMessage message = new ChatSessionMessage();
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(now);
        chatSessionMessageMapper.insert(message);

        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            return;
        }

        if ((session.getTitle() == null || DEFAULT_TITLE.equals(session.getTitle())) && "user".equalsIgnoreCase(role)) {
            session.setTitle(extractTitleFromMessage(content));
        }
        session.setLastMessage(truncate(content, MAX_PREVIEW_LENGTH));
        session.setUpdatedAt(now);
        session.setMessageCount((session.getMessageCount() == null ? 0 : session.getMessageCount()) + 1);
        chatSessionMapper.updateById(session);
        evictSessionListCache(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long userId, String sessionId) {
        validateSessionOwnership(userId, sessionId);

        chatSessionMessageMapper.delete(
                new LambdaQueryWrapper<ChatSessionMessage>()
                        .eq(ChatSessionMessage::getUserId, userId)
                        .eq(ChatSessionMessage::getSessionId, sessionId)
        );

        chatSessionMapper.delete(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getId, sessionId)
                        .eq(ChatSession::getUserId, userId)
        );

        evictSessionListCache(userId);
    }

    private void validateSessionOwnership(Long userId, String sessionId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw new IllegalArgumentException("Session not found");
        }
    }

    private ChatSessionSummaryResponse toSummary(ChatSession session) {
        return ChatSessionSummaryResponse.builder()
                .sessionId(session.getId())
                .title(session.getTitle())
                .lastMessage(session.getLastMessage())
                .messageCount(session.getMessageCount())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }

    private ChatSessionMessageResponse toMessage(ChatSessionMessage msg) {
        return ChatSessionMessageResponse.builder()
                .id(msg.getId())
                .sessionId(msg.getSessionId())
                .role(msg.getRole())
                .content(msg.getContent())
                .createdAt(msg.getCreatedAt())
                .build();
    }

    private String cleanTitle(String title) {
        if (title == null || title.isBlank()) {
            return DEFAULT_TITLE;
        }
        return truncate(title.trim(), MAX_TITLE_LENGTH);
    }

    private String extractTitleFromMessage(String message) {
        if (message == null || message.isBlank()) {
            return DEFAULT_TITLE;
        }
        return truncate(message.trim(), MAX_TITLE_LENGTH);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private String getSessionListCacheKey(Long userId) {
        return "chat:sessions:user:" + userId;
    }

    private void evictSessionListCache(Long userId) {
        try {
            stringRedisTemplate.delete(getSessionListCacheKey(userId));
        } catch (DataAccessException e) {
            log.warn("Failed to evict session cache for user {}", userId, e);
        }
    }
}
