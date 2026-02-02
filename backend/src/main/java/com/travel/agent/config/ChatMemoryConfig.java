package com.travel.agent.config;

import com.travel.agent.ai.memory.PostgresChatMemoryStore;
import com.travel.agent.ai.memory.RedisChatMemoryStore;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * ChatMemory配置
 * 支持Redis（高性能）或PostgreSQL（持久化）
 */
@Slf4j
@Configuration
public class ChatMemoryConfig {

    @Value("${chat.memory.store:redis}")
    private String memoryStoreType;

    @Bean
    @Primary
    public ChatMemoryStore chatMemoryStore(
            RedisChatMemoryStore redisChatMemoryStore,
            PostgresChatMemoryStore postgresChatMemoryStore) {
        
        if ("postgres".equalsIgnoreCase(memoryStoreType)) {
            log.info("✅ Using PostgreSQL ChatMemoryStore (persistent)");
            return postgresChatMemoryStore;
        } else {
            log.info("✅ Using Redis ChatMemoryStore (high-performance)");
            return redisChatMemoryStore;
        }
    }
}
