package com.travel.agent.mapper;

import com.travel.agent.entity.ItineraryVersions;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * <p>
 * Itinerary version history Mapper 接口
 * </p>
 *
 * @author Seren
 * @since 2025-10-19
 */
public interface ItineraryVersionsMapper extends BaseMapper<ItineraryVersions> {

    /**
     * PostgreSQL jsonb insert（避免将 JSON 参数按 varchar 写入 jsonb 列）
     */
    int insertJsonb(ItineraryVersions itineraryVersion);
}
