package com.travel.agent.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;

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

    /**
     * Chroma向量存储Bean
     * 使用API V2以支持Chroma 0.7.0+版本
     * 参考: https://github.com/langchain4j/langchain4j-examples/blob/main/chroma-example
     */
    @Bean
    @Primary
    public EmbeddingStore<TextSegment> chromaEmbeddingStore() {
        log.info("Initializing ChromaEmbeddingStore with API V2: url={}, collection={}", 
                chromaUrl, chromaCollection);
        
        return ChromaEmbeddingStore.builder()
                .apiVersion(V2)
                .baseUrl(chromaUrl)
                .collectionName(chromaCollection)
                .build();
    }
}
