package com.travel.agent.ai.tools;

import com.travel.agent.service.MapboxGeocodingService;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 地理编码工具
 * 将地址转换为经纬度坐标
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeocodingTool implements AgentTool {
    
    private final MapboxGeocodingService mapboxService;
    private final com.travel.agent.monitoring.AgentMetricsService metricsService;
    
    @Override
    public String getName() {
        return "geocode_location";
    }
    
    @Override
    public String getDescription() {
        return "Converts location names or addresses to geographic coordinates (latitude, longitude). " +
               "Supports batch geocoding for multiple locations.";
    }
    
    @Override
    public ToolCategory getCategory() {
        return ToolCategory.GEOCODING;
    }
    
    @Override
    public boolean isParallelizable() {
        return true;  // 支持并行执行
    }
    
    /**
     * 单个地址地理编码
     */
    @Tool("Get coordinates for a location")
    public Coordinates geocode(
        @P("location name or address") String location
    ) {
        log.info("🗺️ Geocoding Tool: {}", location);
        
        // 记录工具调用指标
        io.micrometer.core.instrument.Timer.Sample sample = metricsService.startToolCall("geocode");
        
        try {
            Map<String, BigDecimal> result = mapboxService.geocodeAddress(location);
            
            metricsService.stopToolCall(sample, "geocode");
            
            if (result != null && result.containsKey("latitude") && result.containsKey("longitude")) {
                return Coordinates.builder()
                    .latitude(result.get("latitude"))
                    .longitude(result.get("longitude"))
                    .location(location)
                    .success(true)
                    .build();
            }
            
            return Coordinates.builder()
                .location(location)
                .success(false)
                .errorMessage("Location not found")
                .build();
                
        } catch (Exception e) {
            log.error("Geocoding failed for: {}", location, e);
            return Coordinates.builder()
                .location(location)
                .success(false)
                .errorMessage(e.getMessage())
                .build();
        }
    }
    
    /**
     * 批量地理编码（并行执行）
     */
    @Tool("Batch geocode multiple locations in parallel")
    public List<Coordinates> batchGeocode(
        @P("list of location names") List<String> locations
    ) {
        log.info("🗺️ Batch Geocoding Tool: {} locations", locations.size());
        
        // 并行执行地理编码
        List<CompletableFuture<Coordinates>> futures = locations.stream()
            .map(location -> CompletableFuture.supplyAsync(() -> geocode(location)))
            .collect(Collectors.toList());
        
        // 等待所有完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        return futures.stream()
            .map(CompletableFuture::join)
            .collect(Collectors.toList());
    }
    
    @Override
    public Object execute(Map<String, Object> parameters) {
        if (parameters.containsKey("locations")) {
            @SuppressWarnings("unchecked")
            List<String> locations = (List<String>) parameters.get("locations");
            return batchGeocode(locations);
        } else {
            String location = (String) parameters.get("location");
            return geocode(location);
        }
    }
    
    @Override
    public ToolSpecification getSpecification() {
        // Auto-generate from @Tool annotated methods
        List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(this);
        return specs.isEmpty() ? null : specs.get(0);
    }
}
