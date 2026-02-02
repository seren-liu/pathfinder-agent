package com.travel.agent.ai.nodes;

import com.travel.agent.ai.state.TravelPlanningState;
import com.travel.agent.ai.tools.AttractionInfo;
import com.travel.agent.ai.tools.RAGSearchTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * RAG 检索节点
 * 从知识库检索真实景点信息
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RAGRetrievalNode implements AsyncNodeAction<TravelPlanningState> {
    
    private final RAGSearchTool ragTool;
    
    @Override
    public CompletableFuture<Map<String, Object>> apply(TravelPlanningState state) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("🔍 RAG Retrieval Node: Searching for attractions in {}", 
                    state.getDestination());
            
            try {
                // 计算需要检索的景点数量（每天4个活动）
                int maxResults = state.getDurationDays() * 4;
                
                // 调用 RAG 工具
                List<AttractionInfo> attractions = ragTool.searchAttractions(
                    state.getDestination(),
                    maxResults
                );
                
                log.info("✅ Retrieved {} attractions from knowledge base", attractions.size());
                
                // 转换为 Map 格式以便序列化
                List<Map<String, Object>> attractionMaps = new ArrayList<>();
                for (AttractionInfo attr : attractions) {
                    Map<String, Object> attrMap = new HashMap<>();
                    attrMap.put("name", attr.getName());
                    attrMap.put("category", attr.getCategory());
                    attrMap.put("price", attr.getPrice());
                    attrMap.put("description", attr.getDescription());
                    attrMap.put("relevanceScore", attr.getRelevanceScore());
                    attrMap.put("city", attr.getCity());
                    attractionMaps.add(attrMap);
                }
                
                // 更新状态
                return Map.of(
                    "attractions", attractionMaps,
                    "currentStep", "RAG retrieval completed",
                    "stepCount", state.getStepCount() != null ? state.getStepCount() + 1 : 2,
                    "progress", 30,
                    "progressMessage", String.format("Found %d real attractions", attractions.size())
                );
                
            } catch (Exception e) {
                log.error("❌ RAG retrieval failed", e);
                return Map.of(
                    "errorMessage", "RAG retrieval failed: " + e.getMessage(),
                    "attractions", new ArrayList<>()  // 空列表，继续流程
                );
            }
        });
    }
}
