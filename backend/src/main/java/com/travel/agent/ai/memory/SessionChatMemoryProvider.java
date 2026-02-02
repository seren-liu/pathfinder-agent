package com.travel.agent.ai.memory;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话级别的ChatMemory提供者
 * 为每个sessionId创建独立的ChatMemory
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionChatMemoryProvider {

    private final ChatMemoryStore chatMemoryStore;
    
    @Value("${chat.memory.max-messages:20}")
    private int maxMessages;
    
    // 缓存ChatMemory实例，避免重复创建
    private final Map<Object, ChatMemory> memories = new ConcurrentHashMap<>();

    /**
     * 获取或创建指定会话的ChatMemory
     */
    public ChatMemory get(Object sessionId) {
        return memories.computeIfAbsent(sessionId, id -> {
            log.info("Creating new ChatMemory for session: {}", id);
            return MessageWindowChatMemory.builder()
                    .id(id)
                    .maxMessages(maxMessages)
                    .chatMemoryStore(chatMemoryStore)
                    .build();
        });
    }

    /**
     * 清除指定会话的ChatMemory
     */
    public void clear(Object sessionId) {
        ChatMemory memory = memories.remove(sessionId);
        if (memory != null) {
            memory.clear();
            log.info("Cleared ChatMemory for session: {}", sessionId);
        }
    }

    /**
     * 获取当前活跃会话数
     */
    public int getActiveSessionCount() {
        return memories.size();
    }

    /**
     * 清除所有过期的ChatMemory（可定期调用）
     */
    public void evictInactive() {
        log.debug("Current active sessions: {}", memories.size());
        // TODO: 实现基于时间的自动清理
    }
}
