package com.travel.agent.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.agent.ai.state.TravelPlanningState;
import com.travel.agent.ai.tools.*;
import com.travel.agent.service.AIService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ReAct 模式的旅行规划 Agent
 * 实现 Reasoning (推理) + Acting (行动) 循环
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReActTravelAgent {
    
    private final AIService aiService;
    private final RAGSearchTool ragTool;
    private final GeocodingTool geoTool;
    private final BudgetValidationTool budgetTool;
    private final NearbySearchTool nearbyTool;
    private final ObjectMapper objectMapper;
    
    private static final int MAX_ITERATIONS = 10;
    
    /**
     * 执行 ReAct 循环
     */
    public TravelPlanningState execute(TravelPlanningState initialState) {
        TravelPlanningState state = initialState;
        List<ReActStep> history = new ArrayList<>();
        
        log.info("🚀 Starting ReAct Agent execution");
        
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            log.info("🔄 ReAct Iteration {}/{}", i + 1, MAX_ITERATIONS);
            
            // 1. Thought: Agent 思考下一步
            String thought = reason(state, history);
            log.info("💭 Thought: {}", thought);
            
            // 2. Action: 选择并执行工具
            ActionResult actionResult = act(state, thought);
            log.info("⚡ Action: {} → {}", actionResult.getToolName(), 
                    actionResult.getSuccess() ? "Success" : "Failed");
            
            // 3. Observation: 观察结果并更新状态
            state = observe(state, actionResult);
            log.info("👁️ Observation: {}", actionResult.getObservation());
            
            // 记录步骤
            history.add(ReActStep.builder()
                .iteration(i + 1)
                .thought(thought)
                .action(actionResult.getToolName())
                .observation(actionResult.getObservation())
                .success(actionResult.getSuccess())
                .build());
            
            // 4. 判断是否完成
            if (isComplete(state, thought, actionResult)) {
                log.info("✅ ReAct loop completed after {} iterations", i + 1);
                break;
            }
            
            // 5. 检查是否陷入循环
            if (isLooping(history)) {
                log.warn("⚠️ Detected loop, breaking out");
                break;
            }
        }
        
        // 保存推理历史到状态 - 创建新的状态对象
        Map<String, Object> newStateData = new HashMap<>(state.data());
        Map<String, Object> metadata = new HashMap<>();
        if (state.getMetadata() != null) {
            metadata.putAll(state.getMetadata());
        }
        metadata.put("reactHistory", history);
        newStateData.put("metadata", metadata);
        
        return new TravelPlanningState(newStateData);
    }
    
    /**
     * Reasoning: Agent 思考下一步
     */
    private String reason(TravelPlanningState state, List<ReActStep> history) {
        String prompt = buildReasoningPrompt(state, history);
        String thought = aiService.chat(prompt);
        return thought.trim();
    }
    
    /**
     * 构建推理 Prompt
     */
    private String buildReasoningPrompt(TravelPlanningState state, List<ReActStep> history) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("""
            You are a travel planning agent using the ReAct (Reasoning + Acting) framework.
            
            Your goal: Plan a complete travel itinerary for the user.
            
            === CURRENT STATE ===
            """);
        
        prompt.append(String.format("""
            - Destination: %s, %s
            - Duration: %d days
            - Budget: $%s AUD
            - Party size: %d
            """,
            state.getDestination(),
            state.getDestinationCountry(),
            state.getDurationDays(),
            state.getBudget(),
            state.getPartySize()
        ));
        
        // 当前已有的数据
        if (state.getAttractions() != null && !state.getAttractions().isEmpty()) {
            prompt.append(String.format("- Attractions found: %d\n", state.getAttractions().size()));
        }
        if (state.getBudgetCheck() != null && !state.getBudgetCheck().isEmpty()) {
            Object withinBudget = state.getBudgetCheck().get("withinBudget");
            prompt.append(String.format("- Budget validated: %s\n", 
                Boolean.TRUE.equals(withinBudget) ? "Within budget" : "Over budget"));
        }
        if (state.getItinerary() != null && !state.getItinerary().isEmpty()) {
            prompt.append(String.format("- Itinerary generated: %d days\n", state.getItinerary().size()));
        }
        
        prompt.append("\n=== AVAILABLE TOOLS ===\n");
        prompt.append("""
            1. search_attractions - Search for real attractions from knowledge base
            2. validate_budget - Check if attractions fit within budget
            3. generate_itinerary - Generate the final itinerary
            4. FINISH - Complete the task
            """);
        
        // 历史记录
        if (!history.isEmpty()) {
            prompt.append("\n=== PREVIOUS STEPS ===\n");
            int startIdx = Math.max(0, history.size() - 3);
            for (int i = startIdx; i < history.size(); i++) {
                ReActStep step = history.get(i);
                prompt.append(String.format("Step %d:\n", step.getIteration()));
                prompt.append(String.format("  Thought: %s\n", truncate(step.getThought(), 100)));
                prompt.append(String.format("  Action: %s\n", step.getAction()));
                prompt.append(String.format("  Result: %s\n\n", step.getSuccess() ? "Success" : "Failed"));
            }
        }
        
        prompt.append("""
            
            === YOUR TASK ===
            Think step by step about what to do next.
            
            Respond ONLY with:
            Thought: [your reasoning]
            Action: [tool_name]
            
            Example:
            Thought: I need to find real attractions before planning.
            Action: search_attractions
            
            Your response:
            """);
        
        return prompt.toString();
    }
    
    /**
     * Acting: 执行工具
     */
    private ActionResult act(TravelPlanningState state, String thought) {
        try {
            // 解析 Action
            String action = extractAction(thought);
            
            if (action.equalsIgnoreCase("FINISH")) {
                return ActionResult.builder()
                    .toolName("FINISH")
                    .success(true)
                    .observation("Task completed")
                    .build();
            }
            
            // 执行工具
            return executeToolByName(action, state);
            
        } catch (Exception e) {
            log.error("Action execution failed", e);
            return ActionResult.builder()
                .toolName("ERROR")
                .success(false)
                .observation("Failed to execute action: " + e.getMessage())
                .error(e.getMessage())
                .build();
        }
    }
    
    /**
     * 根据工具名称执行工具
     */
    private ActionResult executeToolByName(String toolName, TravelPlanningState state) {
        log.info("🔧 Executing tool: {}", toolName);
        
        try {
            switch (toolName.toLowerCase()) {
                case "search_attractions":
                    return executeSearchAttractions(state);
                    
                case "validate_budget":
                    return executeValidateBudget(state);
                    
                case "generate_itinerary":
                    return executeGenerateItinerary(state);
                    
                default:
                    return ActionResult.builder()
                        .toolName(toolName)
                        .success(false)
                        .observation("Unknown tool: " + toolName)
                        .build();
            }
        } catch (Exception e) {
            log.error("Tool execution failed: {}", toolName, e);
            return ActionResult.builder()
                .toolName(toolName)
                .success(false)
                .observation("Tool execution failed: " + e.getMessage())
                .error(e.getMessage())
                .build();
        }
    }
    
    /**
     * 执行景点搜索
     */
    private ActionResult executeSearchAttractions(TravelPlanningState state) {
        int maxResults = state.getDurationDays() * 4;
        List<AttractionInfo> attractions = ragTool.searchAttractions(
            state.getDestination(),
            maxResults
        );
        
        if (attractions.isEmpty()) {
            return ActionResult.builder()
                .toolName("search_attractions")
                .success(false)
                .observation("No attractions found for " + state.getDestination())
                .build();
        }
        
        return ActionResult.builder()
            .toolName("search_attractions")
            .success(true)
            .observation(String.format("Found %d attractions in %s", 
                attractions.size(), state.getDestination()))
            .result(attractions)
            .build();
    }
    
    /**
     * 执行预算验证
     */
    private ActionResult executeValidateBudget(TravelPlanningState state) {
        if (state.getAttractions() == null || state.getAttractions().isEmpty()) {
            return ActionResult.builder()
                .toolName("validate_budget")
                .success(false)
                .observation("No attractions to validate. Search attractions first.")
                .build();
        }
        
        // 转换为 AttractionInfo
        List<AttractionInfo> attractions = new ArrayList<>();
        for (Map<String, Object> attrMap : state.getAttractions()) {
            AttractionInfo attr = AttractionInfo.builder()
                .name((String) attrMap.get("name"))
                .category((String) attrMap.get("category"))
                .price((String) attrMap.get("price"))
                .build();
            attractions.add(attr);
        }
        
        BudgetValidation validation = budgetTool.validate(attractions, state.getBudget());
        
        return ActionResult.builder()
            .toolName("validate_budget")
            .success(true)
            .observation(String.format("Budget check: %s (Total: $%s, Budget: $%s)", 
                validation.getWithinBudget() ? "Within budget" : "Over budget",
                validation.getTotalCost(),
                validation.getBudget()))
            .result(validation)
            .build();
    }
    
    /**
     * 执行行程生成（简化版）
     */
    private ActionResult executeGenerateItinerary(TravelPlanningState state) {
        if (state.getAttractions() == null || state.getAttractions().isEmpty()) {
            return ActionResult.builder()
                .toolName("generate_itinerary")
                .success(false)
                .observation("No attractions available. Search attractions first.")
                .build();
        }
        
        // 简单生成行程（实际应该调用 AI）
        List<Map<String, Object>> itinerary = new ArrayList<>();
        for (int day = 1; day <= state.getDurationDays(); day++) {
            Map<String, Object> dayPlan = new HashMap<>();
            dayPlan.put("dayNumber", day);
            dayPlan.put("theme", "Day " + day);
            dayPlan.put("activities", new ArrayList<>());
            itinerary.add(dayPlan);
        }
        
        return ActionResult.builder()
            .toolName("generate_itinerary")
            .success(true)
            .observation(String.format("Generated %d-day itinerary", state.getDurationDays()))
            .result(itinerary)
            .build();
    }
    
    /**
     * Observation: 观察结果并更新状态
     */
    private TravelPlanningState observe(TravelPlanningState state, ActionResult actionResult) {
        if (!actionResult.getSuccess()) {
            return state;
        }
        
        // 根据工具类型更新状态 - 创建新的可修改 Map
        Map<String, Object> stateData = new HashMap<>(state.data());
        
        switch (actionResult.getToolName().toLowerCase()) {
            case "search_attractions":
                if (actionResult.getResult() instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<AttractionInfo> attractions = (List<AttractionInfo>) actionResult.getResult();
                    List<Map<String, Object>> attractionMaps = new ArrayList<>();
                    for (AttractionInfo attr : attractions) {
                        Map<String, Object> attrMap = new HashMap<>();
                        attrMap.put("name", attr.getName());
                        attrMap.put("category", attr.getCategory());
                        attrMap.put("price", attr.getPrice());
                        attrMap.put("description", attr.getDescription());
                        attractionMaps.add(attrMap);
                    }
                    stateData.put("attractions", attractionMaps);
                }
                break;
                
            case "validate_budget":
                if (actionResult.getResult() instanceof BudgetValidation) {
                    BudgetValidation validation = (BudgetValidation) actionResult.getResult();
                    Map<String, Object> budgetCheck = new HashMap<>();
                    budgetCheck.put("totalCost", validation.getTotalCost());
                    budgetCheck.put("budget", validation.getBudget());
                    budgetCheck.put("withinBudget", validation.getWithinBudget());
                    budgetCheck.put("remaining", validation.getRemaining());
                    stateData.put("budgetCheck", budgetCheck);
                }
                break;
                
            case "generate_itinerary":
                if (actionResult.getResult() instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> itinerary = (List<Map<String, Object>>) actionResult.getResult();
                    stateData.put("itinerary", itinerary);
                }
                break;
        }
        
        return new TravelPlanningState(stateData);
    }
    
    /**
     * 判断是否完成
     */
    private boolean isComplete(TravelPlanningState state, String thought, ActionResult actionResult) {
        // 如果 Action 是 FINISH
        if ("FINISH".equalsIgnoreCase(actionResult.getToolName())) {
            return true;
        }
        
        // 如果已经生成了行程
        if (state.getItinerary() != null && !state.getItinerary().isEmpty()) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 检查是否陷入循环
     */
    private boolean isLooping(List<ReActStep> history) {
        if (history.size() < 3) {
            return false;
        }
        
        // 检查最近3步是否重复相同的动作
        int size = history.size();
        String lastAction = history.get(size - 1).getAction();
        String secondLastAction = history.get(size - 2).getAction();
        String thirdLastAction = history.get(size - 3).getAction();
        
        return lastAction.equals(secondLastAction) && secondLastAction.equals(thirdLastAction);
    }
    
    /**
     * 从 thought 中提取 Action
     */
    private String extractAction(String thought) {
        Pattern pattern = Pattern.compile("Action:\\s*([\\w_]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(thought);
        
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        // 如果没有找到，尝试查找常见的工具名
        String lowerThought = thought.toLowerCase();
        if (lowerThought.contains("search") || lowerThought.contains("attraction")) {
            return "search_attractions";
        } else if (lowerThought.contains("budget") || lowerThought.contains("validate")) {
            return "validate_budget";
        } else if (lowerThought.contains("generate") || lowerThought.contains("itinerary")) {
            return "generate_itinerary";
        } else if (lowerThought.contains("finish") || lowerThought.contains("complete")) {
            return "FINISH";
        }
        
        return "search_attractions";  // 默认
    }
    
    /**
     * 截断字符串
     */
    private String truncate(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...";
    }
}
