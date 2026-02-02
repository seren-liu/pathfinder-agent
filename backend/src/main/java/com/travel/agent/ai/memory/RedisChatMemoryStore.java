package com.travel.agent.ai.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis实现的ChatMemoryStore
 * 高性能、支持过期、分布式部署
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisChatMemoryStore implements ChatMemoryStore {

    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String KEY_PREFIX = "chat:memory:";
    private static final long EXPIRATION_HOURS = 24; // 24小时过期

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = KEY_PREFIX + memoryId;
        List<Object> jsonMessages = redisTemplate.opsForList().range(key, 0, -1);
        
        if (jsonMessages == null || jsonMessages.isEmpty()) {
            return new ArrayList<>();
        }

        List<ChatMessage> messages = new ArrayList<>();
        for (Object json : jsonMessages) {
            try {
                messages.add(ChatMessageDeserializer.messageFromJson((String) json));
            } catch (Exception e) {
                log.error("Failed to deserialize message: {}", json, e);
            }
        }
        
        log.debug("Retrieved {} messages from Redis for session: {}", messages.size(), memoryId);
        return messages;
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String key = KEY_PREFIX + memoryId;
        
        // 删除旧数据
        redisTemplate.delete(key);
        
        // 存储新消息
        if (!messages.isEmpty()) {
            List<String> jsonMessages = messages.stream()
                    .map(ChatMessageSerializer::messageToJson)
                    .toList();
            
            redisTemplate.opsForList().rightPushAll(key, jsonMessages.toArray());
            
            // 设置过期时间
            redisTemplate.expire(key, EXPIRATION_HOURS, TimeUnit.HOURS);
        }
        
        log.debug("Updated {} messages in Redis for session: {}", messages.size(), memoryId);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String key = KEY_PREFIX + memoryId;
        redisTemplate.delete(key);
        log.debug("Deleted messages from Redis for session: {}", memoryId);
    }
}
