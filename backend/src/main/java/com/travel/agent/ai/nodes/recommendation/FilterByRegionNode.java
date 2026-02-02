package com.travel.agent.ai.nodes.recommendation;

import com.travel.agent.ai.state.RecommendationState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 区域过滤节点
 * 
 * 功能：
 * 1. 根据用户的目的地偏好过滤候选
 * 2. 确保推荐结果符合区域约束
 * 3. 移除不匹配的候选
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FilterByRegionNode implements AsyncNodeAction<RecommendationState> {
    
    @Override
    public CompletableFuture<Map<String, Object>> apply(RecommendationState state) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("🔍 FilterByRegionNode: Filtering candidates by region");
            
            Map<String, Object> updates = new HashMap<>();
            
            try {
                // 更新进度
                updates.put("currentStep", "filtering_by_region");
                updates.put("progress", 50);
                updates.put("progressMessage", "Filtering destinations...");
                
                List<Map<String, Object>> candidates = state.getCandidates();
                Map<String, Object> intent = state.getAnalyzedIntent();
                String destPref = (String) intent.get("destinationPreference");
                
                List<Map<String, Object>> filtered;
                
                if (destPref == null || destPref.isEmpty()) {
                    // 没有目的地偏好，保留所有候选
                    filtered = new ArrayList<>(candidates);
                    log.info("No destination preference, keeping all {} candidates", candidates.size());
                } else {
                    // 根据目的地偏好过滤
                    filtered = candidates.stream()
                        .filter(candidate -> matchesDestinationPreference(candidate, destPref))
                        .collect(Collectors.toList());
                    
                    log.info("Filtered from {} to {} candidates matching '{}'", 
                        candidates.size(), filtered.size(), destPref);
                }
                
                // 如果过滤后没有结果，保留原候选（防止空结果）
                if (filtered.isEmpty() && !candidates.isEmpty()) {
                    log.warn("No candidates match destination preference, keeping original candidates");
                    filtered = new ArrayList<>(candidates);
                }
                
                updates.put("filteredDestinations", filtered);
                
                log.info("✅ Filtering complete: {} destinations", filtered.size());
                
            } catch (Exception e) {
                log.error("❌ FilterByRegionNode failed", e);
                updates.put("errors", List.of("Region filtering failed: " + e.getMessage()));
                updates.put("filteredDestinations", state.getCandidates());
            }
            
            return updates;
        });
    }
    
    /**
     * 检查候选是否匹配目的地偏好
     */
    private boolean matchesDestinationPreference(Map<String, Object> candidate, String destPref) {
        String name = (String) candidate.get("name");
        String country = (String) candidate.get("country");
        
        if (name == null && country == null) {
            return false;
        }
        
        String destPrefLower = destPref.toLowerCase();
        String nameLower = name != null ? name.toLowerCase() : "";
        String countryLower = country != null ? country.toLowerCase() : "";
        
        // 检查是否匹配区域
        if (isRegionMatch(destPrefLower, countryLower, nameLower)) {
            return true;
        }
        
        // 检查是否包含关键词
        return nameLower.contains(destPrefLower) || countryLower.contains(destPrefLower);
    }
    
    /**
     * 检查区域匹配
     */
    private boolean isRegionMatch(String destPref, String country, String name) {
        // 南美洲
        if (destPref.contains("south america") || destPref.contains("南美")) {
            return country.contains("brazil") || country.contains("argentina") || 
                   country.contains("peru") || country.contains("chile") ||
                   country.contains("colombia") || country.contains("ecuador");
        }
        
        // 欧洲
        if (destPref.contains("europe") || destPref.contains("欧洲")) {
            return country.contains("france") || country.contains("italy") || 
                   country.contains("spain") || country.contains("germany") ||
                   country.contains("uk") || country.contains("greece");
        }
        
        // 亚洲
        if (destPref.contains("asia") || destPref.contains("亚洲")) {
            return country.contains("japan") || country.contains("china") || 
                   country.contains("thailand") || country.contains("vietnam") ||
                   country.contains("korea") || country.contains("singapore");
        }
        
        // 北美
        if (destPref.contains("north america") || destPref.contains("北美")) {
            return country.contains("usa") || country.contains("canada") || 
                   country.contains("mexico");
        }
        
        return false;
    }
}
