package com.travel.agent.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * 缓存指标 AOP 切面
 * 自动拦截所有 @Cacheable 注解的方法，记录缓存命中率和响应时间
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class CacheMetricsAspect {
    
    private final CacheMetricsService cacheMetricsService;
    private final CacheManager cacheManager;
    private final MeterRegistry registry;
    
    /**
     * 拦截所有 @Cacheable 注解的方法
     */
    @Around("@annotation(cacheable)")
    public Object aroundCacheable(ProceedingJoinPoint joinPoint, Cacheable cacheable) throws Throwable {
        String cacheName = cacheable.value().length > 0 ? cacheable.value()[0] : "default";
        
        long startTime = System.currentTimeMillis();
        boolean cacheHit = false;
        
        try {
            // 检查缓存是否命中（在方法执行前）
            Object cacheKey = generateCacheKey(joinPoint, cacheable);
            cacheHit = isCacheHit(cacheName, cacheKey);
            
            // 执行方法
            Object result = joinPoint.proceed();
            
            long duration = System.currentTimeMillis() - startTime;
            
            // 记录指标
            if (cacheHit) {
                cacheMetricsService.recordCacheHit(cacheName);
            } else {
                cacheMetricsService.recordCacheMiss(cacheName);
            }
            
            cacheMetricsService.recordCacheOperation(cacheName, duration, cacheHit);
            
            log.debug("📊 Cache operation: cache={}, hit={}, duration={}ms", 
                     cacheName, cacheHit, duration);
            
            return result;
            
        } catch (Throwable e) {
            long duration = System.currentTimeMillis() - startTime;
            cacheMetricsService.recordCacheMiss(cacheName);
            cacheMetricsService.recordCacheOperation(cacheName, duration, false);
            throw e;
        }
    }
    
    /**
     * 生成缓存键
     */
    private Object generateCacheKey(ProceedingJoinPoint joinPoint, Cacheable cacheable) {
        Object[] args = joinPoint.getArgs();
        
        // 简化处理：如果有参数，使用第一个参数作为 key
        if (args.length > 0) {
            return args[0];
        }
        
        return joinPoint.getSignature().toShortString();
    }
    
    /**
     * 检查缓存是否命中
     */
    private boolean isCacheHit(String cacheName, Object key) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                Cache.ValueWrapper wrapper = cache.get(key);
                return wrapper != null;
            }
        } catch (Exception e) {
            log.debug("Error checking cache hit: {}", e.getMessage());
        }
        
        return false;
    }
}
