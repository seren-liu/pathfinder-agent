package com.travel.agent.service.impl;

import com.travel.agent.ai.vectorstore.ChromaService;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {
    
    private final ChromaService chromaService;
    private final EmbeddingModel embeddingModel;
    
    @Override
    public void importAllKnowledgeBase() {
        log.info("🚀 Starting to import knowledge base...");
        
        try {
            String[] cities = {"tokyo", "paris", "beijing", "shanghai", "kyoto"};
            int totalChunks = 0;
            
            for (String city : cities) {
                String filePath = "data/knowledge/" + city + "_guide.md";
                Path path = Paths.get(filePath);
                
                if (Files.exists(path)) {
                    String content = Files.readString(path, StandardCharsets.UTF_8);
                    int chunks = importCityGuideInternal(city, content);
                    totalChunks += chunks;
                    log.info("✅ Imported {} guide: {} chunks", city, chunks);
                } else {
                    log.warn("⚠️ File not found: {}", filePath);
                }
            }
            
            log.info("🎉 Knowledge base import completed: {} total chunks", totalChunks);
            
        } catch (Exception e) {
            log.error("❌ Failed to import knowledge base", e);
            throw new RuntimeException("Failed to import knowledge base", e);
        }
    }
    
    @Override
    public void importCityGuide(String city, String content) {
        int chunks = importCityGuideInternal(city, content);
        log.info("✅ Imported {} guide: {} chunks", city, chunks);
    }
    
    private int importCityGuideInternal(String city, String content) {
        try {
            Metadata metadata = new Metadata();
            metadata.put("city", city);
            metadata.put("type", "travel_guide");
            metadata.put("source", city + "_guide.md");
            
            Document document = Document.from(content, metadata);
            
            DocumentSplitter splitter = DocumentSplitters.recursive(500, 50);
            List<TextSegment> segments = splitter.split(document);
            log.info("📄 Split {} guide into {} segments", city, segments.size());
            
            List<TextSegment> enrichedSegments = new ArrayList<>();
            for (TextSegment segment : segments) {
                String text = segment.text();
                Metadata segmentMetadata = segment.metadata().copy();
                
                String attractionName = extractAttractionName(text);
                if (attractionName != null) {
                    segmentMetadata.put("attraction", attractionName);
                }
                
                String price = extractPrice(text);
                if (price != null) {
                    segmentMetadata.put("price", price);
                }
                
                String category = extractCategory(text);
                if (category != null) {
                    segmentMetadata.put("category", category);
                }
                
                enrichedSegments.add(TextSegment.from(text, segmentMetadata));
            }
            
            List<Embedding> embeddings = embeddingModel.embedAll(enrichedSegments).content();
            List<String> ids = chromaService.addAll(embeddings, enrichedSegments);
            
            log.info("💾 Stored {} segments for {}", ids.size(), city);
            return ids.size();
            
        } catch (Exception e) {
            log.error("Failed to import {} guide", city, e);
            throw new RuntimeException("Failed to import " + city + " guide", e);
        }
    }
    
    private String extractAttractionName(String text) {
        Pattern pattern = Pattern.compile("^###\\s+(.+)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }
    
    private String extractPrice(String text) {
        Pattern pattern = Pattern.compile("[¥€$£]\\s*\\d+[,\\d]*");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group().trim();
        }
        return null;
    }
    
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
