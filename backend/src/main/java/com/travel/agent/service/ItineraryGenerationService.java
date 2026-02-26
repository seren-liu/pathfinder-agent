package com.travel.agent.service;

import com.travel.agent.dto.request.GenerateItineraryRequest;
import java.util.concurrent.CompletableFuture;

/**
 * 行程生成服务接口
 */
public interface ItineraryGenerationService {
    
    /**
     * 异步生成行程
     */
    CompletableFuture<Void> generateItineraryAsync(Long tripId, GenerateItineraryRequest request);
    
    /**
     * 获取生成进度
     */
    Integer getGenerationProgress(Long tripId);
    
    /**
     * 获取当前步骤描述
     */
    String getCurrentStep(Long tripId);

    /**
     * 获取生成状态（Redis 中的临时状态）
     */
    String getGenerationStatus(Long tripId);

    /**
     * 获取生成失败错误信息
     */
    String getGenerationErrorMessage(Long tripId);
    
    /**
     * 更新生成进度
     */
    void updateProgress(Long tripId, Integer progress, String step);
}
