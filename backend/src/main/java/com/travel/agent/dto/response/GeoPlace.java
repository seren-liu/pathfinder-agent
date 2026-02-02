package com.travel.agent.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Geoapify Places API response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "GeoPlace", description = "Geoapify place information")
public class GeoPlace {

    @Schema(description = "Place ID")
    private String id;

    @Schema(description = "Place name")
    private String name;

    @Schema(description = "Place type")
    private String type;

    @Schema(description = "Place description")
    private String description;

    @Schema(description = "Latitude")
    private Double latitude;

    @Schema(description = "Longitude")
    private Double longitude;

    @Schema(description = "Place tags")
    private List<String> tags;

    @Schema(description = "Rating")
    private Double rating;

    @Schema(description = "Price level (1-3)")
    private Integer priceLevel;

    @Schema(description = "Price range")
    private String priceRange;

    @Schema(description = "Opening hours")
    private String openingHours;

    @Schema(description = "Image URL")
    private String imageUrl;
}
