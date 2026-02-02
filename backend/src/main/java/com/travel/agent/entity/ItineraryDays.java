package com.travel.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * Daily itinerary
 * </p>
 *
 * @author Seren
 * @since 2025-10-19
 */
@Getter
@Setter
@TableName("itinerary_days")
@Schema(description = "Daily itinerary")
public class ItineraryDays implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "Trip ID")
    private Long tripId;

    @Schema(description = "Day number")
    private Integer dayNumber;

    @Schema(description = "Date")
    private LocalDate date;

    @Schema(description = "Daily theme")
    private String theme;

    @Schema(description = "Created at")
    private LocalDateTime createdAt;
}
