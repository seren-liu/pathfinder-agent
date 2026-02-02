package com.travel.agent.ai.memory;

import com.travel.agent.entity.ConversationHistory;
import com.travel.agent.mapper.ConversationHistoryMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * PostgreSQL实现的ChatMemoryStore
 * 将对话历史持久化到数据库
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostgresChatMemoryStore implements ChatMemoryStore {

    private final ConversationHistoryMapper conversationHistoryMapper;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String sessionId = memoryId.toString();
        log.debug("Getting messages for session: {}", sessionId);

        // 从数据库获取对话历史
        List<ConversationHistory> histories = conversationHistoryMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ConversationHistory>()
                .eq(ConversationHistory::getSessionId, sessionId)
                .orderByAsc(ConversationHistory::getCreatedAt)
        );

        // 转换为ChatMessage
        return histories.stream()
                .map(h -> {
                    try {
                        return ChatMessageDeserializer.messageFromJson(h.getMessage());
                    } catch (Exception e) {
                        log.error("Failed to deserialize message for session {}: {}", sessionId, h.getMessage(), e);
                        return null;
                    }
                })
                .filter(msg -> msg != null)
                .collect(Collectors.toList());
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String sessionId = memoryId.toString();
        log.debug("Updating {} messages for session: {}", messages.size(), sessionId);

        // 删除旧消息
        conversationHistoryMapper.delete(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ConversationHistory>()
                .eq(ConversationHistory::getSessionId, sessionId)
        );

        // 保存新消息
        for (ChatMessage message : messages) {
            ConversationHistory history = new ConversationHistory();
            history.setSessionId(sessionId);
            history.setRole(message.type().toString().toLowerCase());
            history.setMessage(ChatMessageSerializer.messageToJson(message));
            history.setCreatedAt(LocalDateTime.now());
            
            conversationHistoryMapper.insert(history);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String sessionId = memoryId.toString();
        log.debug("Deleting messages for session: {}", sessionId);

        conversationHistoryMapper.delete(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ConversationHistory>()
                .eq(ConversationHistory::getSessionId, sessionId)
        );
    }
}
