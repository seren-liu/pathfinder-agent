package com.travel.agent.ai.nodes.recommendation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;
    
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
                
                // 统一拷贝，避免修改原状态数据
                List<Map<String, Object>> enriched = new ArrayList<>();
                List<Integer> missingReasonIndices = new ArrayList<>();
                for (int i = 0; i < recommendations.size(); i++) {
                    Map<String, Object> rec = recommendations.get(i);
                    Map<String, Object> enrichedRec = new HashMap<>(rec);
                    Object reason = enrichedRec.get("recommendReason");
                    if (reason == null || reason.toString().isBlank()) {
                        missingReasonIndices.add(i);
                    }
                    enriched.add(enrichedRec);
                }

                // 批量一次性生成理由（Top3 一次调用）
                if (!missingReasonIndices.isEmpty()) {
                    Map<Integer, String> reasonByIndex = generateReasonsBatch(state, enriched, missingReasonIndices);
                    for (Integer index : missingReasonIndices) {
                        Map<String, Object> rec = enriched.get(index);
                        String reason = reasonByIndex.getOrDefault(index + 1, buildFallbackReason(rec));
                        rec.put("recommendReason", reason);
                    }
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
    
    private Map<Integer, String> generateReasonsBatch(
            RecommendationState state,
            List<Map<String, Object>> recommendations,
            List<Integer> targetIndices
    ) {
        try {
            String prompt = buildBatchReasonPrompt(state, recommendations, targetIndices);
            String response = aiService.chat(prompt);
            return parseReasonMap(response);
        } catch (Exception e) {
            log.error("Failed to generate recommendation reasons in batch", e);
            return Collections.emptyMap();
        }
    }

    private String buildBatchReasonPrompt(
            RecommendationState state,
            List<Map<String, Object>> recommendations,
            List<Integer> targetIndices
    ) {
        StringBuilder destinations = new StringBuilder();
        for (Integer index : targetIndices) {
            Map<String, Object> destination = recommendations.get(index);
            destinations.append(String.format(
                    Locale.ROOT,
                    "- index: %d, name: %s, country: %s, description: %s, features: %s%n",
                    index + 1,
                    String.valueOf(destination.get("name")),
                    String.valueOf(destination.get("country")),
                    String.valueOf(destination.get("description")),
                    String.valueOf(destination.get("features"))
            ));
        }

        return String.format(
                Locale.ROOT,
                """
                Generate personalized recommendation reasons for the following travel destinations.
                Return JSON only.

                User Preferences:
                - Destination Preference: %s
                - Interests: %s
                - Mood: %s
                - Budget Level: %d
                - Duration: %d days

                Destinations:
                %s

                Output JSON format:
                [
                  {"index": 1, "reason": "2-3 sentence recommendation reason"},
                  {"index": 2, "reason": "2-3 sentence recommendation reason"}
                ]
                """,
                state.getDestinationPreference(),
                String.join(", ", state.getInterests()),
                state.getMood(),
                state.getBudgetLevel(),
                state.getDays(),
                destinations.toString()
        );
    }

    private Map<Integer, String> parseReasonMap(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return Collections.emptyMap();
        }

        try {
            String cleaned = extractJsonBlock(rawResponse);
            JsonNode root = objectMapper.readTree(cleaned);
            JsonNode entries = root.isArray() ? root : root.get("reasons");
            if (entries == null || !entries.isArray()) {
                return Collections.emptyMap();
            }

            Map<Integer, String> reasonByIndex = new HashMap<>();
            for (JsonNode item : entries) {
                JsonNode idxNode = item.get("index");
                JsonNode reasonNode = item.get("reason");
                if (idxNode == null || reasonNode == null) {
                    continue;
                }
                int index = idxNode.asInt();
                String reason = reasonNode.asText("").trim();
                if (index > 0 && !reason.isEmpty()) {
                    reasonByIndex.put(index, reason);
                }
            }
            return reasonByIndex;
        } catch (Exception e) {
            log.warn("Failed to parse batch recommendation reasons: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private String extractJsonBlock(String response) {
        String cleaned = response.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }
        return cleaned;
    }

    private String buildFallbackReason(Map<String, Object> destination) {
        return String.format(
                Locale.ROOT,
                "%s is a strong match for your interests and travel style.",
                String.valueOf(destination.get("name"))
        );
    }
}
