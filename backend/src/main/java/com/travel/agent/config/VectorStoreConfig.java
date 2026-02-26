package com.travel.agent.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import static dev.langchain4j.store.embedding.chroma.ChromaApiVersion.V2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 向量数据库配置类
 * 根据LangChain4j官方示例配置Chroma向量存储
 */
@Slf4j
@Configuration
public class VectorStoreConfig {

    @Value("${langchain4j.chroma.base-url}")
    private String chromaUrl;

    @Value("${langchain4j.chroma.collection-name}")
    private String chromaCollection;

    @Value("${langchain4j.chroma.enabled:true}")
    private boolean chromaEnabled;

    @Value("${langchain4j.chroma.fail-fast:false}")
    private boolean chromaFailFast;

    /**
     * Chroma向量存储Bean
     * 使用API V2以支持Chroma 0.7.0+版本
     * 参考: https://github.com/langchain4j/langchain4j-examples/blob/main/chroma-example
     */
    @Bean
    @Primary
    public EmbeddingStore<TextSegment> chromaEmbeddingStore() {
        if (!chromaEnabled) {
            log.warn("Chroma embedding store disabled by config, using InMemoryEmbeddingStore");
            return new InMemoryEmbeddingStore<>();
        }

        log.info("Initializing ChromaEmbeddingStore with API V2: url={}, collection={}", 
                chromaUrl, chromaCollection);

        try {
            return ChromaEmbeddingStore.builder()
                    .apiVersion(V2)
                    .baseUrl(chromaUrl)
                    .collectionName(chromaCollection)
                    .build();
        } catch (Exception e) {
            if (chromaFailFast) {
                throw e;
            }
            log.warn("Failed to initialize ChromaEmbeddingStore, fallback to InMemoryEmbeddingStore: {}", e.getMessage());
            return new InMemoryEmbeddingStore<>();
        }
    }
}
