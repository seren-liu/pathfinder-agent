package com.travel.agent.ai.nodes.recommendation;

import com.travel.agent.ai.state.RecommendationState;
import com.travel.agent.service.AIService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 知识搜索节点
 * 
 * 功能：
 * 1. 使用 AI 生成候选目的地（不依赖数据库）
 * 2. 基于用户意图生成多样化的候选
 * 3. 为下游节点提供充足的候选池
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchKnowledgeNode implements AsyncNodeAction<RecommendationState> {
    
    private final AIService aiService;
    
    @Override
    public CompletableFuture<Map<String, Object>> apply(RecommendationState state) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("🔍 SearchKnowledgeNode: Searching for destination candidates");
            
            Map<String, Object> updates = new HashMap<>();
            
            try {
                // 更新进度
                updates.put("currentStep", "searching_knowledge");
                updates.put("progress", 30);
                updates.put("progressMessage", "Searching for destinations...");
                
                // 构建搜索 Prompt
                String searchPrompt = buildSearchPrompt(state);
                
                log.debug("Search prompt: {}", searchPrompt);
                
                // 调用 AI 生成候选
                String aiResponse = aiService.chat(searchPrompt);
                
                // 解析候选目的地
                List<Map<String, Object>> candidates = parseCandidates(aiResponse);
                
                updates.put("candidates", candidates);
                
                log.info("✅ Found {} destination candidates", candidates.size());
                
            } catch (Exception e) {
                log.error("❌ SearchKnowledgeNode failed", e);
                updates.put("errors", List.of("Knowledge search failed: " + e.getMessage()));
                updates.put("candidates", new ArrayList<>());
            }
            
            return updates;
        });
    }
    
    /**
     * 构建搜索 Prompt
     */
    private String buildSearchPrompt(RecommendationState state) {
        Map<String, Object> intent = state.getAnalyzedIntent();
        String destPref = (String) intent.get("destinationPreference");
        String destType = (String) intent.get("destinationType");
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("Generate 10 diverse travel destination candidates.\n\n");
        
        // 目的地约束
        if (destPref != null && !destPref.isEmpty()) {
            prompt.append(String.format("🎯 CRITICAL: User wants destinations in/related to: \"%s\"\n", destPref));
            prompt.append("You MUST recommend destinations that match this region/preference.\n\n");
        }
        
        // 用户偏好
        prompt.append("User Preferences:\n");
        prompt.append(String.format("- Interests: %s\n", String.join(", ", state.getInterests())));
        prompt.append(String.format("- Mood: %s\n", state.getMood()));
        prompt.append(String.format("- Budget Level: %d (1=low, 2=medium, 3=high)\n", state.getBudgetLevel()));
        prompt.append(String.format("- Duration: %d days\n\n", state.getDays()));
        
        // 排除列表
        if (!state.getExcludeNames().isEmpty()) {
            prompt.append(String.format("🚫 EXCLUDE: %s\n\n", String.join(", ", state.getExcludeNames())));
        }
        
        // 输出格式
        prompt.append("Return JSON array with format:\n");
        prompt.append("[\n");
        prompt.append("  {\n");
        prompt.append("    \"name\": \"City Name\",\n");
        prompt.append("    \"country\": \"Country\",\n");
        prompt.append("    \"description\": \"Brief description\",\n");
        prompt.append("    \"features\": [\"feature1\", \"feature2\"],\n");
        prompt.append("    \"budgetLevel\": 1-3,\n");
        prompt.append("    \"matchScore\": 0-100\n");
        prompt.append("  }\n");
        prompt.append("]\n\n");
        prompt.append("Return ONLY valid JSON array, no markdown.");
        
        return prompt.toString();
    }
    
    /**
     * 解析候选目的地
     */
    private List<Map<String, Object>> parseCandidates(String aiResponse) {
        try {
            // 清理 JSON
            String cleaned = aiResponse.trim();
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.substring(7);
            }
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(3);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();
            
            // 解析 JSON
            com.google.gson.Gson gson = new com.google.gson.Gson();
            java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<List<Map<String, Object>>>(){}.getType();
            List<Map<String, Object>> candidates = gson.fromJson(cleaned, listType);
            
            return candidates != null ? candidates : new ArrayList<>();
            
        } catch (Exception e) {
            log.error("Failed to parse candidates JSON", e);
            return new ArrayList<>();
        }
    }
}
