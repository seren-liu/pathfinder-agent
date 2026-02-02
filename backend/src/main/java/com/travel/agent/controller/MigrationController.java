package com.travel.agent.controller;

import com.travel.agent.ai.embedding.DestinationEmbeddingService;
import com.travel.agent.ai.vectorstore.ChromaService;
import com.travel.agent.dto.response.CommonResponse;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/migration")
@RequiredArgsConstructor
@Tag(name = "Migration", description = "数据迁移接口（仅开发环境使用）")
public class MigrationController {

    private final DestinationEmbeddingService destinationEmbeddingService;
    private final ChromaService chromaService;

    /**
     * 向量化所有目的地
     */
    @PostMapping("/embed-destinations")
    @Operation(summary = "向量化所有目的地", description = "将数据库中的目的地数据向量化并存储到Chroma")
    public CommonResponse<String> embedDestinations() {
        log.info("🚀 Starting destination embedding migration...");
        
        try {
            destinationEmbeddingService.embedAllDestinations();
            return CommonResponse.success("Successfully embedded all destinations");
        } catch (Exception e) {
            log.error("Failed to embed destinations", e);
            return CommonResponse.error(500, "Migration failed: " + e.getMessage());
        }
    }

    /**
     * 测试向量检索
     */
    @GetMapping("/test-search")
    @Operation(summary = "测试向量检索", description = "测试语义搜索功能")
    public CommonResponse<List<Map<String, Object>>> testSearch(
            @RequestParam String query,
            @RequestParam(defaultValue = "3") int maxResults
    ) {
        log.info("Testing vector search: query='{}', maxResults={}", query, maxResults);
        
        try {
            List<EmbeddingMatch<TextSegment>> matches = chromaService.search(query, maxResults);
            
            List<Map<String, Object>> results = matches.stream()
                    .map(match -> {
                        Map<String, Object> result = new HashMap<>();
                        result.put("score", match.score());
                        result.put("text", match.embedded().text());
                        result.put("metadata", match.embedded().metadata().toMap());
                        return result;
                    })
                    .collect(Collectors.toList());
            
            return CommonResponse.success(results);
        } catch (Exception e) {
            log.error("Search test failed", e);
            return CommonResponse.error(500, "Search failed: " + e.getMessage());
        }
    }
}
