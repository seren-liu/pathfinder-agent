package com.travel.agent.ai.tools;

import com.travel.agent.service.KnowledgeBaseService;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG 检索工具
 * 从知识库中检索真实景点信息
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RAGSearchTool implements AgentTool {
    
    private final KnowledgeBaseService knowledgeBaseService;
    private final com.travel.agent.monitoring.AgentMetricsService metricsService;
    
    @Override
    public String getName() {
        return "search_attractions";
    }
    
    @Override
    public String getDescription() {
        return "Searches the knowledge base for real attractions, prices, opening hours, " +
               "and travel tips for a given destination. Returns detailed information " +
               "about tourist spots, restaurants, hotels, and transportation.";
    }
    
    @Override
    public ToolCategory getCategory() {
        return ToolCategory.KNOWLEDGE_RETRIEVAL;
    }
    
    /**
     * 搜索景点（LangChain4j @Tool 注解）
     */
    @Tool("Search for attractions in the knowledge base")
    public List<AttractionInfo> searchAttractions(
        @P("destination name (e.g., 'Tokyo', 'Paris')") String destination,
        @P("maximum number of results to return") int maxResults
    ) {
        log.info("🔍 RAG Tool: Searching for {} attractions in {}", maxResults, destination);
        
        // 开始记录 RAG 检索指标
        io.micrometer.core.instrument.Timer.Sample sample = metricsService.startRAGSearch();
        
        try {
            List<EmbeddingMatch<TextSegment>> matches = 
                knowledgeBaseService.searchAttractions(destination, maxResults);
            
            // 计算最高相似度分数
            double maxScore = matches.stream()
                .mapToDouble(EmbeddingMatch::score)
                .max()
                .orElse(0.0);
            
            // 记录 RAG 检索完成
            metricsService.stopRAGSearch(sample, maxScore);
            
            return matches.stream()
                .filter(match -> match.score() > 0.7)  // 只返回高相关度结果
                .map(this::extractAttractionInfo)
                .collect(Collectors.toList());
        } catch (Exception e) {
            metricsService.stopRAGSearch(sample, 0.0);
            throw e;
        }
    }
    
    /**
     * 搜索特定类别的信息
     */
    @Tool("Search for specific category of travel information")
    public List<AttractionInfo> searchByCategory(
        @P("destination name") String destination,
        @P("category: attraction, food, accommodation, transport") String category,
        @P("maximum results") int maxResults
    ) {
        log.info("🔍 RAG Tool: Searching {} in {} (category: {})", 
                 maxResults, destination, category);
        
        // 开始记录 RAG 检索指标
        io.micrometer.core.instrument.Timer.Sample sample = metricsService.startRAGSearch();
        
        try {
            String query = String.format("%s %s %s", destination, category, "information");
            List<EmbeddingMatch<TextSegment>> matches = 
                knowledgeBaseService.searchTravelInfo(query, maxResults);
            
            // 计算最高相似度分数
            double maxScore = matches.stream()
                .mapToDouble(EmbeddingMatch::score)
                .max()
                .orElse(0.0);
            
            // 记录 RAG 检索完成
            metricsService.stopRAGSearch(sample, maxScore);
            
            return matches.stream()
                .filter(match -> match.score() > 0.7)
                .filter(match -> matchesCategory(match, category))
                .map(this::extractAttractionInfo)
                .collect(Collectors.toList());
        } catch (Exception e) {
            metricsService.stopRAGSearch(sample, 0.0);
            throw e;
        }
    }
    
    @Override
    public Object execute(Map<String, Object> parameters) {
        String destination = (String) parameters.get("destination");
        Integer maxResults = (Integer) parameters.getOrDefault("maxResults", 10);
        
        if (parameters.containsKey("category")) {
            String category = (String) parameters.get("category");
            return searchByCategory(destination, category, maxResults);
        }
        
        return searchAttractions(destination, maxResults);
    }
    
    @Override
    public ToolSpecification getSpecification() {
        // Auto-generate from @Tool annotated methods
        List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(this);
        return specs.isEmpty() ? null : specs.get(0);
    }
    
    /**
     * 从 EmbeddingMatch 提取景点信息
     */
    private AttractionInfo extractAttractionInfo(EmbeddingMatch<TextSegment> match) {
        TextSegment segment = match.embedded();
        var metadata = segment.metadata();
        
        return AttractionInfo.builder()
            .name(metadata.getString("attraction"))
            .category(metadata.getString("category"))
            .price(metadata.getString("price"))
            .description(segment.text())
            .relevanceScore(match.score())
            .city(metadata.getString("city"))
            .build();
    }
    
    private boolean matchesCategory(EmbeddingMatch<TextSegment> match, String category) {
        String metadataCategory = match.embedded().metadata().getString("category");
        return metadataCategory != null && metadataCategory.equalsIgnoreCase(category);
    }
}
