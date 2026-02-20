package com.travel.agent.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 路线优化服务
 * 使用 Google OR-Tools 进行 TSP 求解，优化每日活动顺序
 */
public interface RouteOptimizationService {

    /**
     * 优化单日活动的访问顺序
     *
     * @param activities      当天的活动列表（Map 格式，包含 name, type, location, startTime 等）
     * @param geoCoordinates  地理编码结果：location -> {latitude, longitude}
     * @param destinationContext 目的地上下文（如 "Tokyo, Japan"），用于地理编码增强
     * @return 优化后的活动列表（重排顺序 + 更新 startTime）
     */
    List<Map<String, Object>> optimizeDayRoute(
        List<Map<String, Object>> activities,
        Map<String, Map<String, BigDecimal>> geoCoordinates,
        String destinationContext
    );

    /**
     * 获取驾驶时间/距离矩阵
     *
     * @param coordinates 坐标列表，每个元素为 [longitude, latitude]
     * @return NxN 矩阵，result[i][j] = 从 i 到 j 的驾驶时间（秒）
     */
    long[][] getDistanceMatrix(List<double[]> coordinates);
}
