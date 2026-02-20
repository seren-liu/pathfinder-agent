package com.travel.agent.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 路线优化专用指标收集器
 * 用于监控路线优化功能的调用情况和性能
 */
@Slf4j
@Component
public class RouteOptimizationMetrics {
    
    private final Counter optimizationAttempts;
    private final Counter optimizationSuccess;
    private final Counter optimizationFailures;
    private final Counter geocodingCalls;
    private final Counter matrixApiCalls;
    private final Counter haversineFallbacks;
    private final Timer tspSolverTimer;
    private final Timer geocodingTimer;
    private final Timer matrixApiTimer;
    
    public RouteOptimizationMetrics(MeterRegistry registry) {
        // 路线优化尝试次数
        this.optimizationAttempts = Counter.builder("route.optimization.attempts")
                .description("Total number of route optimization attempts")
                .tag("component", "route_optimizer")
                .register(registry);
        
        // 路线优化成功次数
        this.optimizationSuccess = Counter.builder("route.optimization.success")
                .description("Number of successful route optimizations")
                .tag("component", "route_optimizer")
                .register(registry);
        
        // 路线优化失败次数
        this.optimizationFailures = Counter.builder("route.optimization.failures")
                .description("Number of failed route optimizations")
                .tag("component", "route_optimizer")
                .register(registry);
        
        // 地理编码调用次数
        this.geocodingCalls = Counter.builder("route.geocoding.calls")
                .description("Number of geocoding API calls")
                .tag("component", "route_optimizer")
                .register(registry);
        
        // Matrix API 调用次数
        this.matrixApiCalls = Counter.builder("route.matrix_api.calls")
                .description("Number of Mapbox Matrix API calls")
                .tag("component", "route_optimizer")
                .register(registry);
        
        // Haversine 降级次数
        this.haversineFallbacks = Counter.builder("route.haversine.fallbacks")
                .description("Number of times Haversine fallback was used")
                .tag("component", "route_optimizer")
                .register(registry);
        
        // TSP 求解器耗时
        this.tspSolverTimer = Timer.builder("route.tsp_solver.duration")
                .description("Time taken to solve TSP")
                .tag("component", "route_optimizer")
                .register(registry);
        
        // 地理编码耗时
        this.geocodingTimer = Timer.builder("route.geocoding.duration")
                .description("Time taken for geocoding")
                .tag("component", "route_optimizer")
                .register(registry);
        
        // Matrix API 耗时
        this.matrixApiTimer = Timer.builder("route.matrix_api.duration")
                .description("Time taken for Matrix API call")
                .tag("component", "route_optimizer")
                .register(registry);
    }
    
    // ========== 计数器方法 ==========
    
    public void recordOptimizationAttempt() {
        optimizationAttempts.increment();
        log.debug("📊 Metric: route.optimization.attempts++");
    }
    
    public void recordOptimizationSuccess() {
        optimizationSuccess.increment();
        log.debug("📊 Metric: route.optimization.success++");
    }
    
    public void recordOptimizationFailure() {
        optimizationFailures.increment();
        log.debug("📊 Metric: route.optimization.failures++");
    }
    
    public void recordGeocodingCall() {
        geocodingCalls.increment();
        log.debug("📊 Metric: route.geocoding.calls++");
    }
    
    public void recordMatrixApiCall() {
        matrixApiCalls.increment();
        log.debug("📊 Metric: route.matrix_api.calls++");
    }
    
    public void recordHaversineFallback() {
        haversineFallbacks.increment();
        log.debug("📊 Metric: route.haversine.fallbacks++");
    }
    
    // ========== 计时器方法 ==========
    
    public Timer.Sample startTspSolver() {
        return Timer.start();
    }
    
    public void stopTspSolver(Timer.Sample sample) {
        sample.stop(tspSolverTimer);
    }
    
    public Timer.Sample startGeocoding() {
        return Timer.start();
    }
    
    public void stopGeocoding(Timer.Sample sample) {
        sample.stop(geocodingTimer);
    }
    
    public Timer.Sample startMatrixApi() {
        return Timer.start();
    }
    
    public void stopMatrixApi(Timer.Sample sample) {
        sample.stop(matrixApiTimer);
    }
}
