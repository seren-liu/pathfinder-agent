package com.travel.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * Itinerary version history
 * </p>
 *
 * @author Seren
 * @since 2025-10-19
 */
@Getter
@Setter
@TableName("itinerary_versions")
@Schema(description = "Itinerary version history")
public class ItineraryVersions implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long tripId;

    private Integer versionNumber;

    @Schema(description = "Itinerary snapshot")
    private String snapshot;

    @Schema(description = "Description of changes")
    private String changeDescription;

    @Schema(description = "Total cost for this version")
    private BigDecimal totalCost;

    private LocalDateTime createdAt;
}
