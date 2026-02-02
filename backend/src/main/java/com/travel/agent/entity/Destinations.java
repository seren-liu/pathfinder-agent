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
 * Travel destinations
 * </p>
 *
 * @author Seren
 * @since 2025-10-18
 */
@Getter
@Setter
@TableName("destinations")
@Schema(name = "Destinations", description = "Travel destinations")
public class Destinations implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "Destination ID")
    private Long id;

    @Schema(description = "Destination name")
    private String name;

    @Schema(description = "Country")
    private String country;

    @Schema(description = "State/Region (for AU: NSW, VIC, QLD, etc.)")
    private String state;

    @Schema(description = "Latitude coordinate")
    private BigDecimal latitude;

    @Schema(description = "Longitude coordinate")
    private BigDecimal longitude;

    @Schema(description = "Destination description")
    private String description;

    @Schema(description = "Budget level: 1=budget, 2=moderate, 3=luxury")
    private Byte budgetLevel;

    @Schema(description = "Best season to visit")
    private String bestSeason;

    @Schema(description = "Timezone: Australia/Sydney")
    private String timezone;

    @Schema(description = "Created timestamp")
    private LocalDateTime createdAt;
}
