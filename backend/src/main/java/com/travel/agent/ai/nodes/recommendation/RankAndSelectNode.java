package com.travel.agent.ai.nodes.recommendation;

import com.travel.agent.ai.state.RecommendationState;
import com.travel.agent.service.AIService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 排序与选择节点
 * 
 * 功能：
 * 1. 使用 AI 对候选目的地进行排序
 * 2. 选择 Top 3 作为最终推荐
 * 3. 确保多样性和质量
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankAndSelectNode implements AsyncNodeAction<RecommendationState> {
    
    private final AIService aiService;
    
    @Override
    public CompletableFuture<Map<String, Object>> apply(RecommendationState state) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("🔍 RankAndSelectNode: Ranking and selecting top destinations");
            
            Map<String, Object> updates = new HashMap<>();
            
            try {
                // 更新进度
                updates.put("currentStep", "ranking_and_selecting");
                updates.put("progress", 70);
                updates.put("progressMessage", "Ranking destinations...");
                
                List<Map<String, Object>> filtered = state.getFilteredDestinations();
                
                if (filtered.isEmpty()) {
                    log.warn("No filtered destinations to rank");
                    updates.put("rankedDestinations", new ArrayList<>());
                    updates.put("recommendations", new ArrayList<>());
                    return updates;
                }
                
                // 如果候选数量 <= 3，直接使用
                if (filtered.size() <= 3) {
                    log.info("Only {} candidates, using all", filtered.size());
                    updates.put("rankedDestinations", filtered);
                    updates.put("recommendations", filtered);
                    return updates;
                }
                
                // 使用 AI 排序
                List<Map<String, Object>> ranked = rankWithAI(state, filtered);
                
                // 选择 Top 3
                List<Map<String, Object>> top3 = ranked.stream()
                    .limit(3)
                    .collect(Collectors.toList());
                
                updates.put("rankedDestinations", ranked);
                updates.put("recommendations", top3);
                
                log.info("✅ Selected top 3 from {} candidates", ranked.size());
                
            } catch (Exception e) {
                log.error("❌ RankAndSelectNode failed", e);
                updates.put("errors", List.of("Ranking failed: " + e.getMessage()));
                
                // 降级：使用简单排序
                List<Map<String, Object>> filtered = state.getFilteredDestinations();
                List<Map<String, Object>> top3 = filtered.stream()
                    .sorted((a, b) -> {
                        Number scoreA = (Number) a.getOrDefault("matchScore", 0);
                        Number scoreB = (Number) b.getOrDefault("matchScore", 0);
                        return Double.compare(scoreB.doubleValue(), scoreA.doubleValue());
                    })
                    .limit(3)
                    .collect(Collectors.toList());
                
                updates.put("rankedDestinations", top3);
                updates.put("recommendations", top3);
            }
            
            return updates;
        });
    }
    
    /**
     * 使用 AI 排序候选
     */
    private List<Map<String, Object>> rankWithAI(RecommendationState state, List<Map<String, Object>> candidates) {
        try {
            // 构建排序 Prompt
            String rankPrompt = buildRankPrompt(state, candidates);
            
            // 调用 AI
            String aiResponse = aiService.chat(rankPrompt);
            
            // 解析排序结果
            return parseRankedResults(aiResponse, candidates);
            
        } catch (Exception e) {
            log.error("AI ranking failed, using fallback", e);
            // 降级：按 matchScore 排序
            return candidates.stream()
                .sorted((a, b) -> {
                    Number scoreA = (Number) a.getOrDefault("matchScore", 0);
                    Number scoreB = (Number) b.getOrDefault("matchScore", 0);
                    return Double.compare(scoreB.doubleValue(), scoreA.doubleValue());
                })
                .collect(Collectors.toList());
        }
    }
    
    /**
     * 构建排序 Prompt
     */
    private String buildRankPrompt(RecommendationState state, List<Map<String, Object>> candidates) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Rank these travel destinations based on user preferences.\n\n");
        
        // 用户偏好
        prompt.append("User Preferences:\n");
        prompt.append(String.format("- Destination Preference: %s\n", state.getDestinationPreference()));
        prompt.append(String.format("- Interests: %s\n", String.join(", ", state.getInterests())));
        prompt.append(String.format("- Mood: %s\n", state.getMood()));
        prompt.append(String.format("- Budget Level: %d\n", state.getBudgetLevel()));
        prompt.append(String.format("- Duration: %d days\n\n", state.getDays()));
        
        // 候选列表
        prompt.append("Candidates:\n");
        for (int i = 0; i < candidates.size(); i++) {
            Map<String, Object> candidate = candidates.get(i);
            prompt.append(String.format("%d. %s, %s - %s\n", 
                i + 1,
                candidate.get("name"),
                candidate.get("country"),
                candidate.get("description")));
        }
        
        prompt.append("\nReturn JSON array of indices in ranked order (best first):\n");
        prompt.append("[1, 3, 2, ...]\n\n");
        prompt.append("Return ONLY the JSON array, no explanation.");
        
        return prompt.toString();
    }
    
    /**
     * 解析排序结果
     */
    private List<Map<String, Object>> parseRankedResults(String aiResponse, List<Map<String, Object>> candidates) {
        try {
            String cleaned = aiResponse.trim()
                .replaceAll("```json", "")
                .replaceAll("```", "")
                .trim();
            
            com.google.gson.Gson gson = new com.google.gson.Gson();
            java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<List<Integer>>(){}.getType();
            List<Integer> indices = gson.fromJson(cleaned, listType);
            
            if (indices == null || indices.isEmpty()) {
                return candidates;
            }
            
            // 根据索引重排序
            List<Map<String, Object>> ranked = new ArrayList<>();
            for (Integer index : indices) {
                if (index > 0 && index <= candidates.size()) {
                    ranked.add(candidates.get(index - 1));
                }
            }
            
            // 添加未排序的候选
            for (Map<String, Object> candidate : candidates) {
                if (!ranked.contains(candidate)) {
                    ranked.add(candidate);
                }
            }
            
            return ranked;
            
        } catch (Exception e) {
            log.error("Failed to parse ranking results", e);
            return candidates;
        }
    }
}
