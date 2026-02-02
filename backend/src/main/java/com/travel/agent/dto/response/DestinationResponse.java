package com.travel.agent.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "目的地响应")
public class DestinationResponse {

    @Schema(description = "目的地ID")
    private Long destinationId;

    @Schema(description = "目的地名称")
    private String name;

    @Schema(description = "州/省")
    private String state;

    @Schema(description = "国家")
    private String country;

    @Schema(description = "纬度")
    private BigDecimal latitude;

    @Schema(description = "经度")
    private BigDecimal longitude;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "预算等级")
    private Integer budgetLevel;

    @Schema(description = "推荐天数 (3-5 天)")
    private Integer recommendedDays;

    @Schema(description = "预估费用 (AUD)")
    private Integer estimatedCost;

    @Schema(description = "最佳旅行季节")
    private String bestSeason;

    @Schema(description = "特征标签")
    private List<String> features;

    @Schema(description = "匹配分数 (0-100)")
    private Integer matchScore;

    @Schema(description = "AI生成的推荐理由")
    private String recommendReason;

    @Schema(description = "图片URL")
    private String imageUrl;

    @Schema(description = "天气信息")
    private WeatherInfo weather;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeatherInfo {
        private Double temperature;
        private String description;
        private Integer humidity;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DestinationPlaceInfo {
        private String placeName;
        private String category;
    }
}
