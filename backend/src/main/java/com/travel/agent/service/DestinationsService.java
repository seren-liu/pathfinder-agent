package com.travel.agent.service;

import com.travel.agent.dto.response.DestinationResponse;
import com.travel.agent.dto.response.ParseIntentResponse;
import com.travel.agent.entity.Destinations;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 * Travel destinations 服务类
 * </p>
 *
 * @author Seren
 * @since 2025-10-18
 */
public interface DestinationsService extends IService<Destinations> {

    /**
     * 根据用户意图推荐目的地
     * 
     * @param userId 用户 ID
     * @param parsedIntent 解析的意图
     * @param excludeIds 排除的目的地 ID
     * @param excludeNames 排除的目的地名称（用于 AI 生成的推荐）
     * @param forceRefresh 是否强制刷新（跳过缓存）
     * @return 推荐的目的地列表
     */
    List<DestinationResponse> recommendDestinations(
        Long userId,
        ParseIntentResponse parsedIntent,
        List<Long> excludeIds,
        List<String> excludeNames,
        Boolean forceRefresh
    );

    /**
     * 计算匹配分数
     */
    Integer calculateMatchScore(
        List<String> destinationFeatures,
        List<String> preferredFeatures
    );
}
