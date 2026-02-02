package com.travel.agent.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 临时惊喜路线请求DTO
 */
@Data
@Schema(name = "SurpriseRouteRequest", description = "临时惊喜路线请求")
public class SurpriseRouteRequest {

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    @Schema(description = "用户当前位置纬度", example = "-33.8688")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    @Schema(description = "用户当前位置经度", example = "151.2093")
    private Double longitude;

    @Schema(description = "用户ID（可选）", example = "1")
    private Long userId;

    @Schema(description = "路线半径（公里）", example = "5.0")
    private Double radius = 5.0;

    @Schema(description = "推荐点数量", example = "4")
    private Integer pointCount = 4;
}
