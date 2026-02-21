package com.travel.agent.mapper;

import com.travel.agent.entity.AIRecommendationCache;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 推荐缓存 Mapper 接口
 *
 * @author Seren
 * @since 2025-10-18
 */
@Mapper
public interface AIRecommendationCacheMapper extends BaseMapper<AIRecommendationCache> {

    /**
     * PostgreSQL jsonb insert（避免将 JSON 参数按 varchar 写入 jsonb 列）
     */
    int insertJsonb(AIRecommendationCache cache);
}
