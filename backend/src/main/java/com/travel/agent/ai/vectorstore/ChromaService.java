package com.travel.agent.ai.vectorstore;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChromaService {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    @Qualifier("taskExecutor")
    private final Executor taskExecutor;

    @Value("${agent.rag.embedding-timeout-ms:4000}")
    private long embeddingTimeoutMs;

    @Value("${agent.rag.embedding-max-retries:1}")
    private int embeddingMaxRetries;

    /**
     * 添加文本到向量数据库
     */
    public String addText(String text, Metadata metadata) {
        log.info("Adding text to Chroma: length={}, metadata={}", text.length(), metadata);
        
        // 1. 生成 Embedding
        Embedding embedding = embeddingModel.embed(text).content();
        
        // 2. 创建 TextSegment
        TextSegment segment = TextSegment.from(text, metadata);
        
        // 3. 存储到 Chroma
        String id = embeddingStore.add(embedding, segment);
        
        log.info("✅ Text added to Chroma: id={}", id);
        return id;
    }

    /**
     * 批量添加文本
     */
    public List<String> addTexts(List<String> texts, List<Metadata> metadataList) {
        log.info("Batch adding {} texts to Chroma", texts.size());
        
        // 1. 创建 TextSegments
        List<TextSegment> segments = new java.util.ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            segments.add(TextSegment.from(texts.get(i), metadataList.get(i)));
        }
        
        // 2. 批量生成 Embeddings
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        
        // 3. 批量存储
        List<String> ids = embeddingStore.addAll(embeddings, segments);
        
        log.info("✅ Batch added {} texts to Chroma", ids.size());
        return ids;
    }
    
    /**
     * 批量添加已有embeddings和segments（用于知识库导入）
     */
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> segments) {
        log.info("Batch adding {} pre-embedded segments to Chroma", segments.size());
        List<String> ids = embeddingStore.addAll(embeddings, segments);
        log.info("✅ Batch added {} segments to Chroma", ids.size());
        return ids;
    }

    /**
     * 语义搜索
     */
    public List<EmbeddingMatch<TextSegment>> search(String query, int maxResults) {
        log.info("Searching in Chroma: query='{}', maxResults={}", query, maxResults);

        int totalAttempts = Math.max(1, embeddingMaxRetries + 1);
        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                // 1. 快速生成查询 Embedding（超时即降级）
                Embedding queryEmbedding = CompletableFuture
                        .supplyAsync(() -> embeddingModel.embed(query).content(), taskExecutor)
                        .orTimeout(embeddingTimeoutMs, TimeUnit.MILLISECONDS)
                        .join();

                // 2. 搜索相似向量（使用新的search API）
                dev.langchain4j.store.embedding.EmbeddingSearchRequest searchRequest =
                        dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
                                .queryEmbedding(queryEmbedding)
                                .maxResults(maxResults)
                                .build();

                List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(searchRequest).matches();
                log.info("✅ Found {} matches", matches.size());
                return matches;
            } catch (Exception e) {
                Throwable cause = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
                boolean canRetry = attempt < totalAttempts;
                log.warn(
                        "Embedding query failed (attempt {}/{}): {}",
                        attempt,
                        totalAttempts,
                        cause.getMessage()
                );
                if (!canRetry) {
                    break;
                }
            }
        }

        log.warn("⚠️ Fallback to empty RAG results due to embedding failure: query='{}'", query);
        return List.of();
    }

    /**
     * 带过滤的语义搜索
     */
    public List<EmbeddingMatch<TextSegment>> searchWithFilter(
            String query, 
            int maxResults,
            double minScore
    ) {
        List<EmbeddingMatch<TextSegment>> matches = search(query, maxResults * 2);
        
        // 过滤低分结果
        return matches.stream()
                .filter(match -> match.score() >= minScore)
                .limit(maxResults)
                .collect(Collectors.toList());
    }
    
    /**
     * 根据 metadata 删除文档
     * 注意：ChromaDB 的 Java 客户端可能不直接支持按 metadata 删除
     * 这里提供一个基础实现，实际使用时可能需要根据具体的 EmbeddingStore 实现调整
     */
    public void deleteByMetadata(String metadataKey, String metadataValue) {
        log.info("Attempting to delete documents with {}={}", metadataKey, metadataValue);
        
        // 由于 LangChain4j 的 EmbeddingStore 接口不直接支持按 metadata 删除
        // 我们需要先搜索匹配的文档，然后删除它们
        // 这是一个变通方案，实际生产环境中可能需要直接调用 ChromaDB API
        
        try {
            // 搜索所有可能匹配的文档（使用 metadata value 作为查询）
            List<EmbeddingMatch<TextSegment>> matches = search(metadataValue, 100);
            
            int deletedCount = 0;
            for (EmbeddingMatch<TextSegment> match : matches) {
                TextSegment segment = match.embedded();
                if (segment != null && segment.metadata() != null) {
                    String value = segment.metadata().getString(metadataKey);
                    if (metadataValue.equals(value)) {
                        // 注意：标准的 EmbeddingStore 接口没有 remove 方法
                        // 这里我们记录需要删除的文档，但实际删除可能需要特定实现
                        deletedCount++;
                        log.debug("Found document to delete: {}", match.embeddingId());
                    }
                }
            }
            
            log.info("✅ Marked {} documents for deletion with {}={}", deletedCount, metadataKey, metadataValue);
            
        } catch (Exception e) {
            log.error("Failed to delete documents by metadata", e);
            throw new RuntimeException("Failed to delete documents", e);
        }
    }
    
    /**
     * 删除所有文档（用于测试或重置）
     */
    public void removeAll() {
        log.warn("⚠️ Removing all documents from Chroma");
        embeddingStore.removeAll();
        log.info("✅ All documents removed");
    }
}
