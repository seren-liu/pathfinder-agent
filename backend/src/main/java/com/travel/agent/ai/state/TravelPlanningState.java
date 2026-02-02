package com.travel.agent.ai.state;

import com.travel.agent.ai.tools.Coordinates;
import lombok.Getter;
import org.bsc.langgraph4j.state.AgentState;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 旅行规划 Agent 状态
 * 使用 Map 存储状态数据，符合 LangGraph4j 标准
 */
@Getter
public class TravelPlanningState extends AgentState {
    
    /**
     * 构造函数
     */
    public TravelPlanningState(Map<String, Object> initData) {
        super(initData);
    }
    
    // ==================== 便捷访问方法 ====================
    
    public Long getTripId() {
        return (Long) data().get("tripId");
    }
    
    public String getDestination() {
        return (String) data().get("destination");
    }
    
    public String getDestinationCountry() {
        return (String) data().get("destinationCountry");
    }
    
    public Integer getDurationDays() {
        return (Integer) data().get("durationDays");
    }
    
    public BigDecimal getBudget() {
        Object budget = data().get("budget");
        if (budget instanceof BigDecimal) {
            return (BigDecimal) budget;
        } else if (budget instanceof Number) {
            return new BigDecimal(budget.toString());
        }
        return BigDecimal.ZERO;
    }
    
    public Integer getPartySize() {
        return (Integer) data().get("partySize");
    }
    
    public String getPreferences() {
        return (String) data().get("preferences");
    }
    
    public String getStartDate() {
        return (String) data().get("startDate");
    }
    
    public BigDecimal getDestinationLatitude() {
        Object lat = data().get("destinationLatitude");
        if (lat instanceof BigDecimal) {
            return (BigDecimal) lat;
        } else if (lat instanceof Number) {
            return new BigDecimal(lat.toString());
        }
        return BigDecimal.ZERO;
    }
    
    public BigDecimal getDestinationLongitude() {
        Object lon = data().get("destinationLongitude");
        if (lon instanceof BigDecimal) {
            return (BigDecimal) lon;
        } else if (lon instanceof Number) {
            return new BigDecimal(lon.toString());
        }
        return BigDecimal.ZERO;
    }
    
    @SuppressWarnings("unchecked")
    public List<String> getPlanSteps() {
        return (List<String>) data().getOrDefault("planSteps", new ArrayList<>());
    }
    
    public String getCurrentStep() {
        return (String) data().get("currentStep");
    }
    
    public Integer getStepCount() {
        return (Integer) data().getOrDefault("stepCount", 0);
    }
    
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getAttractions() {
        return (List<Map<String, Object>>) data().getOrDefault("attractions", new ArrayList<>());
    }
    
    @SuppressWarnings("unchecked")
    public Map<String, Map<String, Object>> getGeoData() {
        return (Map<String, Map<String, Object>>) data().getOrDefault("geoData", new HashMap<>());
    }
    
    @SuppressWarnings("unchecked")
    public Map<String, Object> getBudgetCheck() {
        return (Map<String, Object>) data().getOrDefault("budgetCheck", new HashMap<>());
    }
    
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getItinerary() {
        return (List<Map<String, Object>>) data().getOrDefault("itinerary", new ArrayList<>());
    }
    
    public String getAiResponse() {
        return (String) data().get("aiResponse");
    }
    
    @SuppressWarnings("unchecked")
    public List<String> getIssues() {
        return (List<String>) data().getOrDefault("issues", new ArrayList<>());
    }
    
    public Integer getReflectionCount() {
        return (Integer) data().getOrDefault("reflectionCount", 0);
    }
    
    public Boolean getApproved() {
        return (Boolean) data().getOrDefault("approved", false);
    }
    
    @SuppressWarnings("unchecked")
    public List<String> getRevisionSuggestions() {
        return (List<String>) data().getOrDefault("revisionSuggestions", new ArrayList<>());
    }
    
    public Integer getProgress() {
        return (Integer) data().getOrDefault("progress", 0);
    }
    
    public String getProgressMessage() {
        return (String) data().get("progressMessage");
    }
    
    public String getErrorMessage() {
        return (String) data().get("errorMessage");
    }
    
    @SuppressWarnings("unchecked")
    public Map<String, Object> getMetadata() {
        return (Map<String, Object>) data().getOrDefault("metadata", new HashMap<>());
    }
    
    // ==================== 辅助方法：类型转换 ====================
    
    /**
     * 获取强类型的行程列表
     */
    public List<DayPlan> getItineraryTyped() {
        return getItinerary().stream()
            .map(DayPlan::fromMap)
            .toList();
    }
    
    /**
     * 获取强类型的预算检查
     */
    public BudgetCheck getBudgetCheckTyped() {
        return BudgetCheck.fromMap(getBudgetCheck());
    }
    
    /**
     * 获取强类型的地理数据
     */
    @SuppressWarnings("unchecked")
    public Map<String, Coordinates> getGeoDataTyped() {
        Map<String, Map<String, Object>> geoData = getGeoData();
        Map<String, Coordinates> result = new HashMap<>();
        
        for (Map.Entry<String, Map<String, Object>> entry : geoData.entrySet()) {
            Coordinates coord = Coordinates.fromMap(entry.getValue());
            if (coord != null) {
                result.put(entry.getKey(), coord);
            }
        }
        
        return result;
    }
}
