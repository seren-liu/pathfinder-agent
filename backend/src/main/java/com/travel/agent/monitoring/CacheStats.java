package com.travel.agent.monitoring;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 缓存统计数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CacheStats {
    
    /**
     * 缓存名称
     */
    private String cacheName;
    
    /**
     * 缓存命中次数
     */
    private long hits;
    
    /**
     * 缓存未命中次数
     */
    private long misses;
    
    /**
     * 缓存命中率 (0.0 - 1.0)
     */
    private double hitRate;
    
    /**
     * 总请求次数
     */
    private long totalRequests;
    
    /**
     * 平均响应时间 (毫秒)
     */
    private double avgResponseTime;
    
    /**
     * P95 响应时间 (毫秒)
     */
    private double p95ResponseTime;
    
    /**
     * P99 响应时间 (毫秒)
     */
    private double p99ResponseTime;
    
    /**
     * 计算命中率
     */
    public void calculateHitRate() {
        this.totalRequests = hits + misses;
        this.hitRate = totalRequests > 0 ? (double) hits / totalRequests : 0.0;
    }
}
