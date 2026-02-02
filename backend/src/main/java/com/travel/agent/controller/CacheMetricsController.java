package com.travel.agent.controller;

import com.travel.agent.monitoring.CacheMetricsService;
import com.travel.agent.monitoring.CacheStats;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 缓存指标 REST API
 * 提供缓存统计数据查询接口
 */
@Slf4j
@RestController
@RequestMapping("/api/metrics/cache")
@RequiredArgsConstructor
@Tag(name = "Cache Metrics", description = "缓存性能监控 API")
public class CacheMetricsController {
    
    private final CacheMetricsService cacheMetricsService;
    
    /**
     * 获取所有缓存的统计信息
     */
    @GetMapping("/stats")
    @Operation(summary = "获取所有缓存统计", description = "返回所有缓存的命中率、响应时间等指标")
    public Map<String, CacheStats> getAllCacheStats() {
        log.info("📊 Fetching all cache stats");
        return cacheMetricsService.getAllCacheStats();
    }
    
    /**
     * 获取特定缓存的统计信息
     */
    @GetMapping("/{cacheName}/stats")
    @Operation(summary = "获取特定缓存统计", description = "返回指定缓存的详细统计信息")
    public CacheStats getCacheStats(@PathVariable String cacheName) {
        log.info("📊 Fetching cache stats for: {}", cacheName);
        return cacheMetricsService.getCacheStats(cacheName);
    }
    
    /**
     * 重置特定缓存的数据
     */
    @PostMapping("/{cacheName}/reset")
    @Operation(summary = "重置缓存", description = "清空指定缓存的所有数据")
    public void resetCache(@PathVariable String cacheName) {
        log.info("🗑️ Resetting cache: {}", cacheName);
        cacheMetricsService.resetCacheStats(cacheName);
    }
}
