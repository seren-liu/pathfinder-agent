package com.travel.agent.service;

import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.data.segment.TextSegment;

import java.util.List;

/**
 * 知识库服务接口
 * 负责导入旅游指南文档到向量数据库，并提供检索功能
 */
public interface KnowledgeBaseService {
    
    /**
     * 导入所有知识库文档到Chroma
     */
    void importAllKnowledgeBase();
    
    /**
     * 导入单个城市的旅游指南
     */
    void importCityGuide(String city, String content);
    
    /**
     * 搜索相关景点信息
     */
    List<EmbeddingMatch<TextSegment>> searchAttractions(String destination, int maxResults);
    
    /**
     * 搜索相关旅游信息（景点、美食、住宿等）
     */
    List<EmbeddingMatch<TextSegment>> searchTravelInfo(String query, int maxResults);
}
