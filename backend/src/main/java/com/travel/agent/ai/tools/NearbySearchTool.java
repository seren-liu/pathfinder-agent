package com.travel.agent.ai.tools;

import com.travel.agent.dto.response.GeoPlace;
import com.travel.agent.service.GeoapifyService;
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
import java.util.stream.Collectors;

/**
 * 附近地点搜索工具
 * 使用 Geoapify API 搜索附近的POI
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NearbySearchTool implements AgentTool {
    
    private final GeoapifyService geoapifyService;
    
    @Override
    public String getName() {
        return "search_nearby";
    }
    
    @Override
    public String getDescription() {
        return "Searches for nearby points of interest (POI) around a given location. " +
               "Can find restaurants, attractions, hotels, shops, etc. within a specified radius.";
    }
    
    @Override
    public ToolCategory getCategory() {
        return ToolCategory.SEARCH;
    }
    
    /**
     * 搜索附近地点
     */
    @Tool("Find nearby points of interest")
    public List<POI> findNearby(
        @P("center latitude") Double latitude,
        @P("center longitude") Double longitude,
        @P("search radius in kilometers (default: 1.0)") Double radiusKm,
        @P("category: restaurant, attraction, hotel, shop, etc.") String category
    ) {
        log.info("📍 Nearby Tool: Searching {} within {}km of ({}, {})", 
                 category, radiusKm, latitude, longitude);
        
        try {
            // 默认半径1公里
            if (radiusKm == null || radiusKm <= 0) {
                radiusKm = 1.0;
            }
            
            // 调用 Geoapify 服务
            List<GeoPlace> places = geoapifyService.searchNearbyPlaces(
                latitude, 
                longitude, 
                radiusKm, 
                10,  // 限制返回10个结果
                List.of(category)
            );
            
            // 转换为 POI
            return places.stream()
                .map(this::convertToPOI)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Nearby search failed", e);
            return List.of();
        }
    }
    
    @Override
    public Object execute(Map<String, Object> parameters) {
        Double latitude = ((Number) parameters.get("latitude")).doubleValue();
        Double longitude = ((Number) parameters.get("longitude")).doubleValue();
        Double radiusKm = parameters.containsKey("radiusKm") 
            ? ((Number) parameters.get("radiusKm")).doubleValue() 
            : 1.0;
        String category = (String) parameters.getOrDefault("category", "attraction");
        
        return findNearby(latitude, longitude, radiusKm, category);
    }
    
    @Override
    public ToolSpecification getSpecification() {
        // Auto-generate from @Tool annotated methods
        List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(this);
        return specs.isEmpty() ? null : specs.get(0);
    }
    
    /**
     * 转换 GeoPlace 到 POI
     */
    private POI convertToPOI(GeoPlace place) {
        return POI.builder()
            .name(place.getName())
            .category(place.getType() != null ? place.getType() : "unknown")
            .latitude(place.getLatitude() != null ? BigDecimal.valueOf(place.getLatitude()) : null)
            .longitude(place.getLongitude() != null ? BigDecimal.valueOf(place.getLongitude()) : null)
            .address(place.getDescription())
            .distance(null)  // GeoPlace doesn't have distance field
            .build();
    }
}
