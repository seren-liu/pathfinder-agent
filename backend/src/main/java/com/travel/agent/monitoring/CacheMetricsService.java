package com.travel.agent.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 缓存指标服务
 * 记录和查询缓存命中率、响应时间等指标
 */
@Slf4j
@Service
public class CacheMetricsService {
    
    private final MeterRegistry registry;
    private final CacheManager cacheManager;
    
    public CacheMetricsService(MeterRegistry registry, CacheManager cacheManager) {
        this.registry = registry;
        this.cacheManager = cacheManager;
        log.info("✅ CacheMetricsService initialized");
    }
    
    /**
     * 记录缓存命中
     */
    public void recordCacheHit(String cacheName) {
        Counter.builder("cache.hit")
            .tag("cache", cacheName)
            .description("Cache hit count")
            .register(registry)
            .increment();
        
        log.debug("📊 Cache hit recorded: cache={}", cacheName);
    }
    
    /**
     * 记录缓存未命中
     */
    public void recordCacheMiss(String cacheName) {
        Counter.builder("cache.miss")
            .tag("cache", cacheName)
            .description("Cache miss count")
            .register(registry)
            .increment();
        
        log.debug("📊 Cache miss recorded: cache={}", cacheName);
    }
    
    /**
     * 记录缓存操作耗时
     */
    public void recordCacheOperation(String cacheName, long durationMs, boolean hit) {
        Timer.builder("cache.operation.duration")
            .tag("cache", cacheName)
            .tag("result", hit ? "hit" : "miss")
            .description("Cache operation duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry)
            .record(durationMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * 获取缓存命中次数
     */
    public double getCacheHits(String cacheName) {
        Counter counter = registry.find("cache.hit")
            .tag("cache", cacheName)
            .counter();
        return counter != null ? counter.count() : 0.0;
    }
    
    /**
     * 获取缓存未命中次数
     */
    public double getCacheMisses(String cacheName) {
        Counter counter = registry.find("cache.miss")
            .tag("cache", cacheName)
            .counter();
        return counter != null ? counter.count() : 0.0;
    }
    
    /**
     * 获取缓存命中率
     */
    public double getCacheHitRate(String cacheName) {
        double hits = getCacheHits(cacheName);
        double misses = getCacheMisses(cacheName);
        double total = hits + misses;
        
        if (total == 0) {
            return 0.0;
        }
        
        return hits / total;
    }
    
    /**
     * 获取缓存平均响应时间
     */
    public double getAvgResponseTime(String cacheName) {
        Timer timer = registry.find("cache.operation.duration")
            .tag("cache", cacheName)
            .timer();
        
        if (timer != null && timer.count() > 0) {
            return timer.mean(TimeUnit.MILLISECONDS);
        }
        
        return 0.0;
    }
    
    /**
     * 获取缓存 P95 响应时间
     */
    public double getP95ResponseTime(String cacheName) {
        Timer timer = registry.find("cache.operation.duration")
            .tag("cache", cacheName)
            .timer();
        
        if (timer != null && timer.count() > 0) {
            return timer.takeSnapshot().percentileValues()[1].value(TimeUnit.MILLISECONDS);
        }
        
        return 0.0;
    }
    
    /**
     * 获取缓存 P99 响应时间
     */
    public double getP99ResponseTime(String cacheName) {
        Timer timer = registry.find("cache.operation.duration")
            .tag("cache", cacheName)
            .timer();
        
        if (timer != null && timer.count() > 0) {
            return timer.takeSnapshot().percentileValues()[2].value(TimeUnit.MILLISECONDS);
        }
        
        return 0.0;
    }
    
    /**
     * 获取特定缓存的统计信息
     */
    public CacheStats getCacheStats(String cacheName) {
        double hits = getCacheHits(cacheName);
        double misses = getCacheMisses(cacheName);
        
        CacheStats stats = CacheStats.builder()
            .cacheName(cacheName)
            .hits((long) hits)
            .misses((long) misses)
            .avgResponseTime(getAvgResponseTime(cacheName))
            .p95ResponseTime(getP95ResponseTime(cacheName))
            .p99ResponseTime(getP99ResponseTime(cacheName))
            .build();
        
        stats.calculateHitRate();
        
        return stats;
    }
    
    /**
     * 获取所有缓存的统计信息
     */
    public Map<String, CacheStats> getAllCacheStats() {
        Map<String, CacheStats> allStats = new HashMap<>();
        
        // 获取所有已配置的缓存名称
        if (cacheManager != null) {
            for (String cacheName : cacheManager.getCacheNames()) {
                CacheStats stats = getCacheStats(cacheName);
                if (stats.getTotalRequests() > 0) {
                    allStats.put(cacheName, stats);
                }
            }
        }
        
        return allStats;
    }
    
    /**
     * 重置特定缓存的统计数据
     * 注意：Micrometer 的 Counter 不支持重置，这里只是清空缓存内容
     */
    public void resetCacheStats(String cacheName) {
        if (cacheManager != null) {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
                log.info("🗑️ Cache cleared: {}", cacheName);
            }
        }
    }
}
