package com.travel.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * Itinerary activities
 * </p>
 *
 * @author Seren
 * @since 2025-10-19
 */
@Getter
@Setter
@TableName("itinerary_items")
@Schema(description = "Itinerary activities")
public class ItineraryItems implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long dayId;

    private Long tripId;

    @Schema(description = "Activity order")
    private Integer orderIndex;

    private String activityName;

    private String activityType;

    private LocalTime startTime;

    private Integer durationMinutes;

    private String location;

    @Schema(description = "Cost in AUD")
    private BigDecimal cost;

    @Schema(description = "Booking link")
    private String bookingUrl;

    private String status;

    @Schema(description = "Additional notes")
    private String notes;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Schema(description = "GPS latitude")
    private BigDecimal latitude;

    @Schema(description = "GPS longitude")
    private BigDecimal longitude;

    @Schema(description = "Geoapify place ID")
    private String placeId;

    @Schema(description = "Original activity flag")
    private Boolean originalFlag;
}
