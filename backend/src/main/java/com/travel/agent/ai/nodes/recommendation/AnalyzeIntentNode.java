package com.travel.agent.ai.nodes.recommendation;

import com.travel.agent.ai.state.RecommendationState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 意图分析节点
 * 
 * 功能：
 * 1. 分析用户的目的地偏好
 * 2. 提取关键特征
 * 3. 确定推荐策略
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyzeIntentNode implements AsyncNodeAction<RecommendationState> {
    
    @Override
    public CompletableFuture<Map<String, Object>> apply(RecommendationState state) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("🔍 AnalyzeIntentNode: Analyzing user intent");
            
            Map<String, Object> updates = new HashMap<>();
            
            try {
                // 更新进度
                updates.put("currentStep", "analyzing_intent");
                updates.put("progress", 10);
                updates.put("progressMessage", "Analyzing your preferences...");
                
                // 分析意图
                Map<String, Object> analyzedIntent = new HashMap<>();
                
                // 提取目的地偏好
                String destPref = state.getDestinationPreference();
                if (destPref != null && !destPref.isEmpty()) {
                    analyzedIntent.put("destinationPreference", destPref);
                    analyzedIntent.put("destinationType", inferDestinationType(destPref));
                }
                
                // 提取兴趣特征
                analyzedIntent.put("interests", state.getInterests());
                analyzedIntent.put("mood", state.getMood());
                analyzedIntent.put("budgetLevel", state.getBudgetLevel());
                analyzedIntent.put("days", state.getDays());
                
                // 确定搜索策略
                String searchStrategy = determineSearchStrategy(destPref, state.getInterests());
                analyzedIntent.put("searchStrategy", searchStrategy);
                
                updates.put("analyzedIntent", analyzedIntent);
                
                log.info("✅ Intent analyzed: destPref={}, type={}, strategy={}", 
                    destPref, 
                    analyzedIntent.get("destinationType"),
                    searchStrategy);
                
            } catch (Exception e) {
                log.error("❌ AnalyzeIntentNode failed", e);
                updates.put("errors", java.util.List.of("Intent analysis failed: " + e.getMessage()));
            }
            
            return updates;
        });
    }
    
    /**
     * 推断目的地类型
     */
    private String inferDestinationType(String destination) {
        if (destination == null || destination.isEmpty()) {
            return "UNKNOWN";
        }
        
        String lower = destination.toLowerCase();
        
        // 区域
        if (lower.contains("europe") || lower.contains("欧洲") ||
            lower.contains("asia") || lower.contains("亚洲") ||
            lower.contains("america") || lower.contains("美洲") ||
            lower.contains("africa") || lower.contains("非洲")) {
            return "REGION";
        }
        
        // 模糊描述
        if (lower.contains("beach") || lower.contains("海滩") ||
            lower.contains("mountain") || lower.contains("山") ||
            lower.contains("island") || lower.contains("岛")) {
            return "VAGUE";
        }
        
        // 国家
        if (lower.contains("china") || lower.contains("中国") ||
            lower.contains("japan") || lower.contains("日本") ||
            lower.contains("france") || lower.contains("法国")) {
            return "COUNTRY";
        }
        
        // 默认城市
        return "CITY";
    }
    
    /**
     * 确定搜索策略
     */
    private String determineSearchStrategy(String destPref, java.util.List<String> interests) {
        if (destPref != null && !destPref.isEmpty()) {
            return "DESTINATION_FOCUSED";  // 以目的地为主
        } else if (interests != null && !interests.isEmpty()) {
            return "INTEREST_FOCUSED";  // 以兴趣为主
        } else {
            return "GENERAL";  // 通用推荐
        }
    }
}
