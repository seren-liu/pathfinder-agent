package com.travel.agent.service.impl;

import com.travel.agent.ai.vectorstore.ChromaService;
import com.travel.agent.dto.DocumentVersion;
import com.travel.agent.dto.KnowledgeBaseStats;
import com.travel.agent.service.KnowledgeBaseService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 增量知识库服务
 * 支持单文件导入、热更新、版本管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
@org.springframework.context.annotation.Primary
public class IncrementalKnowledgeBaseService implements KnowledgeBaseService {
    
    private final ChromaService chromaService;
    private final EmbeddingModel embeddingModel;
    private final com.travel.agent.monitoring.AgentMetricsService metricsService;
    private final DocumentSplitter splitter = DocumentSplitters.recursive(500, 50);
    
    // 文档版本管理
    private final Map<String, DocumentVersion> documentVersions = new ConcurrentHashMap<>();
    
    // 知识库目录
    private static final String KNOWLEDGE_DIR = "data/knowledge";
    
    /**
     * 启动时初始化文件监听
     */
    @PostConstruct
    public void initialize() {
        log.info("🚀 Initializing Incremental Knowledge Base Service");
        
        // 启动文件监听器
        startFileWatcher();
        
        log.info("✅ Knowledge Base Service initialized");
    }
    
    /**
     * 增量导入单个文档
     */
    public void importDocument(Path documentPath) {
        log.info("📥 Incrementally importing: {}", documentPath);
        
        try {
            // 1. 读取文档内容
            String content = Files.readString(documentPath, StandardCharsets.UTF_8);
            
            // 2. 计算文档哈希
            String contentHash = calculateHash(content);
            String documentId = generateDocumentId(documentPath);
            
            // 3. 检查是否需要更新
            DocumentVersion existingVersion = documentVersions.get(documentId);
            if (existingVersion != null && existingVersion.getContentHash().equals(contentHash)) {
                log.info("📄 Document unchanged, skipping: {}", documentPath.getFileName());
                return;
            }
            
            // 4. 删除旧版本（如果存在）
            if (existingVersion != null) {
                log.info("🗑️ Deleting old version of: {}", documentId);
                deleteDocument(documentId);
            }
            
            // 5. 导入新版本
            int segmentCount = importDocumentInternal(documentPath, content, documentId);
            
            // 记录知识库导入指标
            metricsService.recordKnowledgeBaseImport();
            
            // 6. 更新版本信息
            documentVersions.put(documentId, DocumentVersion.builder()
                .documentId(documentId)
                .filePath(documentPath.toString())
                .contentHash(contentHash)
                .segmentCount(segmentCount)
                .lastUpdated(LocalDateTime.now())
                .version(existingVersion != null ? existingVersion.getVersion() + 1 : 1)
                .build());
            
            log.info("✅ Document imported: {} (v{}, {} segments)", 
                    documentId, 
                    documentVersions.get(documentId).getVersion(),
                    segmentCount);
            
            // 更新知识库统计指标
            updateMetrics();
            
        } catch (Exception e) {
            log.error("❌ Failed to import document: {}", documentPath, e);
            throw new RuntimeException("Failed to import document", e);
        }
    }
    
    /**
     * 导入文档内部实现
     */
    private int importDocumentInternal(Path documentPath, String content, String documentId) {
        try {
            // 提取城市名称
            String city = extractCityName(documentPath);
            
            // 创建元数据
            Metadata metadata = new Metadata();
            metadata.put("document_id", documentId);
            metadata.put("city", city);
            metadata.put("type", "travel_guide");
            metadata.put("source", documentPath.getFileName().toString());
            metadata.put("imported_at", LocalDateTime.now().toString());
            
            // 创建文档
            Document document = Document.from(content, metadata);
            
            // 分块
            List<TextSegment> segments = splitter.split(document);
            log.info("📄 Split {} into {} segments", documentPath.getFileName(), segments.size());
            
            // 增强元数据
            List<TextSegment> enrichedSegments = enrichSegments(segments, city);
            
            // 向量化
            List<Embedding> embeddings = embeddingModel.embedAll(enrichedSegments).content();
            
            // 存储到 Chroma
            List<String> ids = chromaService.addAll(embeddings, enrichedSegments);
            
            log.info("💾 Stored {} segments for {}", ids.size(), city);
            
            return ids.size();
            
        } catch (Exception e) {
            log.error("Failed to import document internally", e);
            throw new RuntimeException("Failed to import document", e);
        }
    }
    
    /**
     * 删除文档
     */
    public void deleteDocument(String documentId) {
        try {
            log.info("🗑️ Deleting document: {}", documentId);
            
            // 从 Chroma 中删除（通过 metadata 过滤）
            chromaService.deleteByMetadata("document_id", documentId);
            
            // 从版本管理中移除
            documentVersions.remove(documentId);
            
            log.info("✅ Document deleted: {}", documentId);
            
        } catch (Exception e) {
            log.error("Failed to delete document: {}", documentId, e);
            throw new RuntimeException("Failed to delete document", e);
        }
    }
    
    /**
     * 启动文件监听器
     */
    private void startFileWatcher() {
        Path knowledgeDir = Paths.get(KNOWLEDGE_DIR);
        
        // 确保目录存在
        try {
            if (!Files.exists(knowledgeDir)) {
                Files.createDirectories(knowledgeDir);
                log.info("📁 Created knowledge directory: {}", knowledgeDir);
            }
        } catch (IOException e) {
            log.error("Failed to create knowledge directory", e);
            return;
        }
        
        try {
            WatchService watchService = FileSystems.getDefault().newWatchService();
            knowledgeDir.register(watchService, 
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE
            );
            
            // 异步监听文件变化
            CompletableFuture.runAsync(() -> {
                log.info("👀 File watcher started for: {}", knowledgeDir);
                
                while (true) {
                    try {
                        WatchKey key = watchService.take();
                        
                        for (WatchEvent<?> event : key.pollEvents()) {
                            WatchEvent.Kind<?> kind = event.kind();
                            
                            if (kind == StandardWatchEventKinds.OVERFLOW) {
                                continue;
                            }
                            
                            @SuppressWarnings("unchecked")
                            WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                            Path changed = pathEvent.context();
                            Path fullPath = knowledgeDir.resolve(changed);
                            
                            // 只处理 .md 文件
                            if (!changed.toString().endsWith(".md")) {
                                continue;
                            }
                            
                            // 处理文件变化
                            handleFileChange(kind, fullPath);
                        }
                        
                        boolean valid = key.reset();
                        if (!valid) {
                            log.warn("⚠️ Watch key no longer valid, stopping file watcher");
                            break;
                        }
                        
                    } catch (InterruptedException e) {
                        log.error("File watcher interrupted", e);
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        log.error("Error in file watcher", e);
                    }
                }
            });
            
        } catch (IOException e) {
            log.error("Failed to start file watcher", e);
        }
    }
    
    /**
     * 处理文件变化
     */
    private void handleFileChange(WatchEvent.Kind<?> kind, Path fullPath) {
        try {
            // 等待文件写入完成
            Thread.sleep(1000);
            
            if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                log.info("📝 File created: {}", fullPath.getFileName());
                importDocument(fullPath);
                
            } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                log.info("✏️ File modified: {}", fullPath.getFileName());
                importDocument(fullPath);
                
            } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                log.info("🗑️ File deleted: {}", fullPath.getFileName());
                String documentId = generateDocumentId(fullPath);
                deleteDocument(documentId);
            }
            
        } catch (Exception e) {
            log.error("Failed to handle file change: {}", fullPath, e);
        }
    }
    
    /**
     * 计算内容哈希
     */
    private String calculateHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            
            return hexString.toString();
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate hash", e);
        }
    }
    
    /**
     * 生成文档 ID
     */
    private String generateDocumentId(Path path) {
        return path.getFileName().toString().replace(".md", "");
    }
    
    /**
     * 提取城市名称
     */
    private String extractCityName(Path path) {
        String fileName = path.getFileName().toString();
        // 假设文件名格式为 "city_guide.md"
        return fileName.replace("_guide.md", "");
    }
    
    /**
     * 增强文本段元数据
     */
    private List<TextSegment> enrichSegments(List<TextSegment> segments, String city) {
        List<TextSegment> enriched = new ArrayList<>();
        
        for (TextSegment segment : segments) {
            String text = segment.text();
            Metadata metadata = segment.metadata().copy();
            
            // 提取景点名称
            String attractionName = extractAttractionName(text);
            if (attractionName != null) {
                metadata.put("attraction", attractionName);
            }
            
            // 提取价格
            String price = extractPrice(text);
            if (price != null) {
                metadata.put("price", price);
            }
            
            // 提取分类
            String category = extractCategory(text);
            if (category != null) {
                metadata.put("category", category);
            }
            
            enriched.add(TextSegment.from(text, metadata));
        }
        
        return enriched;
    }
    
    /**
     * 提取景点名称
     */
    private String extractAttractionName(String text) {
        // 匹配 Markdown 标题
        if (text.startsWith("###")) {
            return text.substring(3).trim().split("\n")[0];
        }
        return null;
    }
    
    /**
     * 提取价格
     */
    private String extractPrice(String text) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("[¥€$£]\\s*\\d+[,\\d]*");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group().trim();
        }
        return null;
    }
    
    /**
     * 提取分类
     */
    private String extractCategory(String text) {
        if (text.contains("景点") || text.contains("attraction") || text.contains("必游")) {
            return "attraction";
        } else if (text.contains("美食") || text.contains("餐") || text.contains("food")) {
            return "food";
        } else if (text.contains("住宿") || text.contains("酒店") || text.contains("hotel")) {
            return "accommodation";
        } else if (text.contains("交通") || text.contains("transport")) {
            return "transport";
        }
        return "general";
    }
    
    /**
     * 更新指标
     */
    private void updateMetrics() {
        int totalDocuments = documentVersions.size();
        int totalSegments = documentVersions.values().stream()
            .mapToInt(DocumentVersion::getSegmentCount)
            .sum();
        
        metricsService.updateKnowledgeBaseStats(totalDocuments, totalSegments);
    }
    
    /**
     * 获取文档版本信息
     */
    public Map<String, DocumentVersion> getDocumentVersions() {
        return new HashMap<>(documentVersions);
    }
    
    /**
     * 获取知识库统计信息
     */
    public KnowledgeBaseStats getStats() {
        return KnowledgeBaseStats.builder()
            .totalDocuments(documentVersions.size())
            .totalSegments(documentVersions.values().stream()
                .mapToInt(DocumentVersion::getSegmentCount)
                .sum())
            .lastUpdated(documentVersions.values().stream()
                .map(DocumentVersion::getLastUpdated)
                .max(LocalDateTime::compareTo)
                .orElse(null))
            .documents(new ArrayList<>(documentVersions.values()))
            .build();
    }
    
    // 实现接口方法
    @Override
    public void importAllKnowledgeBase() {
        log.info("📥 Importing all knowledge base files");
        
        try {
            Path knowledgeDir = Paths.get(KNOWLEDGE_DIR);
            
            if (!Files.exists(knowledgeDir)) {
                log.warn("⚠️ Knowledge directory does not exist: {}", knowledgeDir);
                return;
            }
            
            Files.list(knowledgeDir)
                .filter(path -> path.toString().endsWith(".md"))
                .forEach(this::importDocument);
                
            log.info("✅ All knowledge base files imported");
            
        } catch (IOException e) {
            log.error("Failed to import all knowledge base", e);
            throw new RuntimeException("Failed to import all knowledge base", e);
        }
    }
    
    @Override
    public void importCityGuide(String city, String content) {
        // 创建临时文件并导入
        try {
            Path knowledgeDir = Paths.get(KNOWLEDGE_DIR);
            if (!Files.exists(knowledgeDir)) {
                Files.createDirectories(knowledgeDir);
            }
            
            Path tempFile = knowledgeDir.resolve(city + "_guide.md");
            Files.writeString(tempFile, content, StandardCharsets.UTF_8);
            importDocument(tempFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to import city guide", e);
        }
    }
    
    @Override
    public List<EmbeddingMatch<TextSegment>> searchAttractions(String destination, int maxResults) {
        String query = String.format("%s attractions things to do", destination);
        return chromaService.search(query, maxResults);
    }
    
    @Override
    public List<EmbeddingMatch<TextSegment>> searchTravelInfo(String query, int maxResults) {
        return chromaService.search(query, maxResults);
    }
}
