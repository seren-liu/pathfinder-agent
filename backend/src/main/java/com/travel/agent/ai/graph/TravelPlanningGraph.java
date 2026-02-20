package com.travel.agent.ai.graph;

import com.travel.agent.ai.nodes.*;
import com.travel.agent.ai.state.TravelPlanningState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

/**
 * 旅行规划状态图
 * 定义完整的行程生成工作流
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TravelPlanningGraph {
    
    @Value("${agent.route-optimization.enabled:false}")
    private boolean routeOptimizationEnabled;
    
    private final PlanningNode planningNode;
    private final RAGRetrievalNode ragRetrievalNode;
    private final BudgetValidationNode budgetValidationNode;
    private final ItineraryGenerationNode itineraryGenerationNode;
    private final RouteOptimizationNode routeOptimizationNode;
    private final ReflectionNode reflectionNode;
    private final SaveNode saveNode;
    
    /**
     * 构建状态图
     */
    public CompiledGraph<TravelPlanningState> buildGraph() throws Exception {
        log.info("🏗️ Building Travel Planning State Graph");
        
        // 创建状态图
        var workflow = new StateGraph<>(TravelPlanningState::new);
        
        // 添加节点
        workflow.addNode("planning", planningNode);
        workflow.addNode("rag_retrieval", ragRetrievalNode);
        workflow.addNode("budget_validation", budgetValidationNode);
        workflow.addNode("itinerary_generation", itineraryGenerationNode);
        workflow.addNode("reflection", reflectionNode);
        workflow.addNode("save", saveNode);
        
        // 定义边
        // START -> planning
        workflow.addEdge(START, "planning");
        
        // planning -> rag_retrieval
        workflow.addEdge("planning", "rag_retrieval");
        
        // rag_retrieval -> budget_validation
        workflow.addEdge("rag_retrieval", "budget_validation");
        
        // budget_validation -> itinerary_generation
        workflow.addEdge("budget_validation", "itinerary_generation");
        
        // 条件路由：根据配置决定是否启用路线优化
        if (routeOptimizationEnabled) {
            log.info("🗺️ Route optimization ENABLED");
            workflow.addNode("route_optimization", routeOptimizationNode);
            workflow.addEdge("itinerary_generation", "route_optimization");
            workflow.addEdge("route_optimization", "reflection");
        } else {
            log.info("⚡ Route optimization DISABLED (fast mode)");
            workflow.addEdge("itinerary_generation", "reflection");
        }
        
        // reflection -> save
        workflow.addEdge("reflection", "save");
        
        // save -> END
        workflow.addEdge("save", END);
        
        // 编译图
        CompiledGraph<TravelPlanningState> graph = workflow.compile();
        
        log.info("✅ Travel Planning State Graph built successfully");
        
        return graph;
    }
}
