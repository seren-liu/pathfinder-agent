package com.travel.agent.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 临时惊喜路线响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "SurpriseRouteResponse", description = "临时惊喜路线响应")
public class SurpriseRouteResponse {

    @Schema(description = "路线ID")
    private String routeId;

    @Schema(description = "路线名称")
    private String routeName;

    @Schema(description = "路线描述")
    private String description;

    @Schema(description = "预计总时长（分钟）")
    private Integer estimatedDuration;

    @Schema(description = "预计总距离（公里）")
    private Double estimatedDistance;

    @Schema(description = "推荐点列表")
    private List<RoutePoint> points;

    @Schema(description = "路线坐标点（用于地图绘制）")
    private List<Coordinate> routeCoordinates;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "RoutePoint", description = "路线推荐点")
    public static class RoutePoint {
        @Schema(description = "推荐点ID")
        private String pointId;

        @Schema(description = "推荐点名称")
        private String name;

        @Schema(description = "推荐点类型")
        private String type; // restaurant, cafe, market, attraction

        @Schema(description = "推荐点描述")
        private String description;

        @Schema(description = "纬度")
        private BigDecimal latitude;

        @Schema(description = "经度")
        private BigDecimal longitude;

        @Schema(description = "距离用户位置（公里）")
        private Double distance;

        @Schema(description = "预计停留时间（分钟）")
        private Integer estimatedStayTime;

        @Schema(description = "开放时间")
        private String openingHours;

        @Schema(description = "特色标签")
        private List<String> tags;

        @Schema(description = "图片URL")
        private String imageUrl;

        @Schema(description = "评分")
        private Double rating;

        @Schema(description = "价格等级（1-3）")
        private Integer priceLevel;

        @Schema(description = "价格范围（如：$15-25）")
        private String priceRange;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "Coordinate", description = "坐标点")
    public static class Coordinate {
        @Schema(description = "纬度")
        private BigDecimal latitude;

        @Schema(description = "经度")
        private BigDecimal longitude;
    }
}
