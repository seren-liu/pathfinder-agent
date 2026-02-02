package com.travel.agent.controller;

import com.travel.agent.dto.DocumentVersion;
import com.travel.agent.dto.KnowledgeBaseStats;
import com.travel.agent.dto.response.CommonResponse;
import com.travel.agent.service.KnowledgeBaseService;
import com.travel.agent.service.impl.IncrementalKnowledgeBaseService;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
@Tag(name = "Knowledge Base", description = "知识库管理API")
public class KnowledgeBaseController {
    
    private final KnowledgeBaseService knowledgeBaseService;
    private final IncrementalKnowledgeBaseService incrementalKnowledgeBaseService;
    
    private static final String KNOWLEDGE_DIR = "data/knowledge";
    
    @PostMapping("/import")
    @Operation(summary = "导入知识库", description = "将所有旅游指南文档导入到向量数据库")
    public CommonResponse<String> importKnowledgeBase() {
        log.info("Starting knowledge base import...");
        
        try {
            knowledgeBaseService.importAllKnowledgeBase();
            return CommonResponse.success("Knowledge base imported successfully");
        } catch (Exception e) {
            log.error("Failed to import knowledge base", e);
            return CommonResponse.error("Failed to import: " + e.getMessage());
        }
    }
    
    @GetMapping("/search")
    @Operation(summary = "测试检索", description = "测试向量数据库检索功能")
    public CommonResponse<Map<String, Object>> searchAttractions(
            @RequestParam String destination,
            @RequestParam(defaultValue = "10") int maxResults
    ) {
        log.info("Searching attractions for: {}", destination);
        
        try {
            List<EmbeddingMatch<TextSegment>> matches = 
                knowledgeBaseService.searchAttractions(destination, maxResults);
            
            Map<String, Object> result = new HashMap<>();
            result.put("query", destination);
            result.put("count", matches.size());
            result.put("results", matches.stream().map(match -> {
                Map<String, Object> item = new HashMap<>();
                item.put("score", match.score());
                item.put("text", match.embedded().text());
                item.put("metadata", match.embedded().metadata().toMap());
                return item;
            }).toList());
            
            return CommonResponse.success(result);
        } catch (Exception e) {
            log.error("Failed to search", e);
            return CommonResponse.error("Search failed: " + e.getMessage());
        }
    }
    
    @GetMapping("/stats")
    @Operation(summary = "获取知识库统计信息", description = "获取知识库的文档数量、分段数量等统计信息")
    public CommonResponse<KnowledgeBaseStats> getStats() {
        log.info("Getting knowledge base stats");
        
        try {
            KnowledgeBaseStats stats = incrementalKnowledgeBaseService.getStats();
            return CommonResponse.success(stats);
        } catch (Exception e) {
            log.error("Failed to get stats", e);
            return CommonResponse.error("Failed to get stats: " + e.getMessage());
        }
    }
    
    @GetMapping("/versions")
    @Operation(summary = "获取文档版本列表", description = "获取所有文档的版本信息")
    public CommonResponse<Map<String, DocumentVersion>> getVersions() {
        log.info("Getting document versions");
        
        try {
            Map<String, DocumentVersion> versions = incrementalKnowledgeBaseService.getDocumentVersions();
            return CommonResponse.success(versions);
        } catch (Exception e) {
            log.error("Failed to get versions", e);
            return CommonResponse.error("Failed to get versions: " + e.getMessage());
        }
    }
    
    @PostMapping("/import/{documentId}")
    @Operation(summary = "手动导入单个文档", description = "手动触发单个文档的导入")
    public CommonResponse<String> importDocument(@PathVariable String documentId) {
        log.info("Manually importing document: {}", documentId);
        
        try {
            Path documentPath = Paths.get(KNOWLEDGE_DIR, documentId + "_guide.md");
            
            if (!Files.exists(documentPath)) {
                return CommonResponse.error(404, "Document not found: " + documentId);
            }
            
            incrementalKnowledgeBaseService.importDocument(documentPath);
            return CommonResponse.success("Document imported successfully: " + documentId);
        } catch (Exception e) {
            log.error("Failed to import document: {}", documentId, e);
            return CommonResponse.error("Failed to import: " + e.getMessage());
        }
    }
    
    @DeleteMapping("/{documentId}")
    @Operation(summary = "删除文档", description = "从知识库中删除指定文档")
    public CommonResponse<String> deleteDocument(@PathVariable String documentId) {
        log.info("Deleting document: {}", documentId);
        
        try {
            incrementalKnowledgeBaseService.deleteDocument(documentId);
            return CommonResponse.success("Document deleted successfully: " + documentId);
        } catch (Exception e) {
            log.error("Failed to delete document: {}", documentId, e);
            return CommonResponse.error("Failed to delete: " + e.getMessage());
        }
    }
}
