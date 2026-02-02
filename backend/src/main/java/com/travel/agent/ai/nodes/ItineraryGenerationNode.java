package com.travel.agent.ai.nodes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.agent.ai.state.TravelPlanningState;
import com.travel.agent.service.AIService;
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
 * 行程生成节点
 * 使用 AI 生成详细行程
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItineraryGenerationNode implements AsyncNodeAction<TravelPlanningState> {
    
    private final AIService aiService;
    private final ObjectMapper objectMapper;
    
    @Override
    public CompletableFuture<Map<String, Object>> apply(TravelPlanningState state) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("✍️ Itinerary Generation Node: Generating {}-day itinerary", 
                    state.getDurationDays());
            
            try {
                // 构建增强的 Prompt
                String prompt = buildPrompt(state);
                
                // 调用 AI
                String aiResponse = aiService.chat(prompt);
                
                // 解析响应
                List<Map<String, Object>> itinerary = parseItinerary(aiResponse);
                
                log.info("✅ Generated itinerary with {} days", itinerary.size());
                
                return Map.of(
                    "itinerary", itinerary,
                    "aiResponse", aiResponse,
                    "currentStep", "Itinerary generation completed",
                    "progress", 70,
                    "progressMessage", String.format("Generated %d-day itinerary", itinerary.size())
                );
                
            } catch (Exception e) {
                log.error("❌ Itinerary generation failed", e);
                return Map.of(
                    "errorMessage", "Itinerary generation failed: " + e.getMessage()
                );
            }
        });
    }
    
    private String buildPrompt(TravelPlanningState state) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append(String.format("""
            Generate a %d-day travel itinerary for %s, %s.
            Budget: $%s AUD, Party size: %d
            
            === REAL ATTRACTIONS FROM KNOWLEDGE BASE ===
            """,
            state.getDurationDays(),
            state.getDestination(),
            state.getDestinationCountry(),
            state.getBudget(),
            state.getPartySize()
        ));
        
        // 添加景点信息
        if (state.getAttractions() != null && !state.getAttractions().isEmpty()) {
            for (Map<String, Object> attr : state.getAttractions()) {
                prompt.append(String.format("- %s (%s): %s\n",
                    attr.get("name"), attr.get("category"), attr.get("price")));
            }
        }
        
        // 添加预算信息
        if (state.getBudgetCheck() != null && !state.getBudgetCheck().isEmpty()) {
            prompt.append(String.format("""
                
                === BUDGET ANALYSIS ===
                Total cost: $%s
                Budget: $%s
                Within budget: %s
                """,
                state.getBudgetCheck().get("totalCost"),
                state.getBudgetCheck().get("budget"),
                state.getBudgetCheck().get("withinBudget")
            ));
        }
        
        prompt.append("""
            
            === REQUIREMENTS ===
            1. Use ONLY the attractions listed above (they are real and verified)
            2. Ensure the itinerary fits within the budget
            3. Each day should have 3-4 activities
            4. Include specific times and durations
            5. Return ONLY valid JSON (no markdown, no extra text)
            
            JSON Format:
            {
              "days": [
                {
                  "dayNumber": 1,
                  "theme": "Arrival Day",
                  "activities": [
                    {
                      "name": "...",
                      "type": "accommodation",
                      "startTime": "14:00",
                      "durationMinutes": 60,
                      "estimatedCost": 150,
                      "location": "..."
                    }
                  ]
                }
              ]
            }
            
            Generate complete itinerary (JSON only):
            """);
        
        return prompt.toString();
    }
    
    private List<Map<String, Object>> parseItinerary(String aiResponse) throws Exception {
        // 清理 markdown
        String cleaned = aiResponse.trim()
            .replaceAll("^```json\\s*", "")
            .replaceAll("^```\\s*", "")
            .replaceAll("```$", "")
            .trim();
        
        // 解析 JSON
        JsonNode root = objectMapper.readTree(cleaned);
        JsonNode daysNode = root.get("days");
        
        if (daysNode == null || !daysNode.isArray()) {
            throw new IllegalArgumentException("Invalid itinerary format: missing 'days' array");
        }
        
        // 转换为 List<Map>
        List<Map<String, Object>> itinerary = new ArrayList<>();
        for (JsonNode dayNode : daysNode) {
            Map<String, Object> dayMap = objectMapper.convertValue(dayNode, Map.class);
            itinerary.add(dayMap);
        }
        
        return itinerary;
    }
}
