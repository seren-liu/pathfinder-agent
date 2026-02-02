package com.travel.agent.ai.graph;

import com.travel.agent.ai.nodes.recommendation.*;
import com.travel.agent.ai.state.RecommendationState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.stereotype.Component;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * 推荐系统状态图
 * 
 * 使用 LangGraph4j 实现真正的 Agent 能力：
 * 1. AnalyzeIntentNode - 分析用户意图
 * 2. SearchKnowledgeNode - RAG 搜索候选
 * 3. FilterByRegionNode - 区域过滤
 * 4. RankAndSelectNode - AI 排序选择
 * 5. GenerateReasonsNode - 生成推荐理由
 * 
 * 对比旧实现：
 * - 旧: 单次 Prompt 调用，无状态管理
 * - 新: 多节点编排，状态在节点间传递，可迭代优化
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationGraph {
    
    private final AnalyzeIntentNode analyzeIntentNode;
    private final SearchKnowledgeNode searchKnowledgeNode;
    private final FilterByRegionNode filterByRegionNode;
    private final RankAndSelectNode rankAndSelectNode;
    private final GenerateReasonsNode generateReasonsNode;
    
    /**
     * 构建推荐状态图
     */
    public CompiledGraph<RecommendationState> buildGraph() throws Exception {
        log.info("🏗️ Building Recommendation State Graph");
        
        // 创建状态图
        var workflow = new StateGraph<>(RecommendationState::new);
        
        // 添加节点
        workflow.addNode("analyze_intent", analyzeIntentNode);
        workflow.addNode("search_knowledge", searchKnowledgeNode);
        workflow.addNode("filter_by_region", filterByRegionNode);
        workflow.addNode("rank_and_select", rankAndSelectNode);
        workflow.addNode("generate_reasons", generateReasonsNode);
        
        // 定义边（线性流程）
        workflow.addEdge(START, "analyze_intent");
        workflow.addEdge("analyze_intent", "search_knowledge");
        workflow.addEdge("search_knowledge", "filter_by_region");
        workflow.addEdge("filter_by_region", "rank_and_select");
        workflow.addEdge("rank_and_select", "generate_reasons");
        workflow.addEdge("generate_reasons", END);
        
        // 编译图
        CompiledGraph<RecommendationState> graph = workflow.compile();
        
        log.info("✅ Recommendation State Graph built successfully");
        log.info("📊 Graph structure: analyze_intent → search_knowledge → filter_by_region → rank_and_select → generate_reasons");
        
        return graph;
    }
}
