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
 * 生成推荐理由节点
 * 
 * 功能：
 * 1. 为每个推荐目的地生成个性化推荐理由
 * 2. 确保理由与用户偏好相关
 * 3. 完善推荐数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GenerateReasonsNode implements AsyncNodeAction<RecommendationState> {
    
    private final AIService aiService;
    
    @Override
    public CompletableFuture<Map<String, Object>> apply(RecommendationState state) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("🔍 GenerateReasonsNode: Generating recommendation reasons");
            
            Map<String, Object> updates = new HashMap<>();
            
            try {
                // 更新进度
                updates.put("currentStep", "generating_reasons");
                updates.put("progress", 90);
                updates.put("progressMessage", "Generating recommendations...");
                
                List<Map<String, Object>> recommendations = state.getRecommendations();
                
                if (recommendations.isEmpty()) {
                    log.warn("No recommendations to generate reasons for");
                    updates.put("completed", true);
                    updates.put("progress", 100);
                    return updates;
                }
                
                // 为每个推荐生成理由
                List<Map<String, Object>> enriched = new ArrayList<>();
                for (Map<String, Object> rec : recommendations) {
                    Map<String, Object> enrichedRec = new HashMap<>(rec);
                    
                    // 如果已有推荐理由，跳过
                    if (rec.containsKey("recommendReason") && rec.get("recommendReason") != null) {
                        enriched.add(enrichedRec);
                        continue;
                    }
                    
                    // 生成推荐理由
                    String reason = generateReason(state, rec);
                    enrichedRec.put("recommendReason", reason);
                    
                    enriched.add(enrichedRec);
                }
                
                updates.put("recommendations", enriched);
                updates.put("completed", true);
                updates.put("progress", 100);
                updates.put("progressMessage", "Recommendations ready!");
                
                log.info("✅ Generated reasons for {} recommendations", enriched.size());
                
            } catch (Exception e) {
                log.error("❌ GenerateReasonsNode failed", e);
                updates.put("errors", List.of("Reason generation failed: " + e.getMessage()));
                updates.put("completed", true);
                updates.put("progress", 100);
            }
            
            return updates;
        });
    }
    
    /**
     * 生成推荐理由
     */
    private String generateReason(RecommendationState state, Map<String, Object> destination) {
        try {
            String prompt = buildReasonPrompt(state, destination);
            String reason = aiService.chat(prompt);
            return reason.trim();
        } catch (Exception e) {
            log.error("Failed to generate reason for {}", destination.get("name"), e);
            return String.format("%s is a great destination that matches your preferences.", 
                destination.get("name"));
        }
    }
    
    /**
     * 构建推荐理由 Prompt
     */
    private String buildReasonPrompt(RecommendationState state, Map<String, Object> destination) {
        return String.format("""
            Generate a personalized recommendation reason (2-3 sentences) for this destination.
            
            Destination: %s, %s
            Description: %s
            Features: %s
            
            User Preferences:
            - Destination Preference: %s
            - Interests: %s
            - Mood: %s
            - Budget Level: %d
            - Duration: %d days
            
            The reason should:
            1. Explain why this destination matches the user's destination preference (if any)
            2. Highlight features that align with user interests
            3. Be engaging and persuasive
            
            Return ONLY the recommendation reason text, no prefix or explanation.
            """,
            destination.get("name"),
            destination.get("country"),
            destination.get("description"),
            destination.get("features"),
            state.getDestinationPreference(),
            String.join(", ", state.getInterests()),
            state.getMood(),
            state.getBudgetLevel(),
            state.getDays()
        );
    }
}
