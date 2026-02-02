package com.travel.agent.controller;

import com.travel.agent.dto.request.SurpriseRouteRequest;
import com.travel.agent.dto.response.CommonResponse;
import com.travel.agent.dto.response.SurpriseRouteResponse;
import com.travel.agent.service.SurpriseRouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 临时惊喜路线控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/surprise-route")
@RequiredArgsConstructor
@Tag(name = "Surprise Route", description = "临时惊喜路线相关接口")
public class SurpriseRouteController {

    private final SurpriseRouteService surpriseRouteService;

    /**
     * 生成临时惊喜路线
     */
    @GetMapping
    @Operation(summary = "生成临时惊喜路线", description = "根据用户位置生成个性化探索路线")
    public CommonResponse<SurpriseRouteResponse> generateSurpriseRoute(
            @Parameter(description = "用户当前位置纬度", required = true, example = "-33.8688")
            @RequestParam Double lat,
            
            @Parameter(description = "用户当前位置经度", required = true, example = "151.2093")
            @RequestParam Double lng,
            
            @Parameter(description = "用户ID（可选）", example = "1")
            @RequestParam(required = false) Long userId,
            
            @Parameter(description = "搜索半径（公里）", example = "5.0")
            @RequestParam(defaultValue = "5.0") Double radius,
            
            @Parameter(description = "推荐点数量", example = "4")
            @RequestParam(defaultValue = "4") Integer pointCount
    ) {
        log.info("Generating surprise route for location: {}, {}, userId: {}", lat, lng, userId);

        SurpriseRouteRequest request = new SurpriseRouteRequest();
        request.setLatitude(lat);
        request.setLongitude(lng);
        request.setUserId(userId);
        request.setRadius(radius);
        request.setPointCount(pointCount);

        try {
            SurpriseRouteResponse response = surpriseRouteService.generateSurpriseRoute(request);
            log.info("Successfully generated surprise route with {} points", response.getPoints().size());
            return CommonResponse.success(response);
        } catch (Exception e) {
            log.error("Failed to generate surprise route", e);
            return CommonResponse.error(500, "Failed to generate surprise route: " + e.getMessage());
        }
    }

    /**
     * 重新生成路线
     */
    @PostMapping("/regenerate")
    @Operation(summary = "重新生成路线", description = "排除已访问的点，生成新的惊喜路线")
    public CommonResponse<SurpriseRouteResponse> regenerateSurpriseRoute(
            @Valid @RequestBody SurpriseRouteRequest request,
            
            @Parameter(description = "要排除的点ID列表")
            @RequestParam(required = false) List<String> excludePointIds
    ) {
        log.info("Regenerating surprise route for location: {}, {}, excluding: {}", 
                request.getLatitude(), request.getLongitude(), excludePointIds);

        try {
            SurpriseRouteResponse response = surpriseRouteService.regenerateSurpriseRoute(
                    request, excludePointIds != null ? excludePointIds : List.of());
            log.info("Successfully regenerated surprise route with {} points", response.getPoints().size());
            return CommonResponse.success(response);
        } catch (Exception e) {
            log.error("Failed to regenerate surprise route", e);
            return CommonResponse.error(500, "Failed to regenerate surprise route: " + e.getMessage());
        }
    }

    /**
     * 获取路线详情
     */
    @GetMapping("/{routeId}")
    @Operation(summary = "获取路线详情", description = "根据路线ID获取详细信息")
    public CommonResponse<SurpriseRouteResponse> getRouteDetails(
            @Parameter(description = "路线ID", required = true)
            @PathVariable String routeId
    ) {
        log.info("Getting route details for routeId: {}", routeId);
        
        // 这里可以实现路线详情获取逻辑
        // 目前返回一个简单的响应
        return CommonResponse.error(404, "Route details not implemented yet");
    }
}
