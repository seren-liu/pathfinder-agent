package com.travel.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * Trip plans
 * </p>
 *
 * @author Seren
 * @since 2025-10-19
 */
@Getter
@Setter
@TableName("trips")
@Schema(description = "Trip plans")
public class Trips implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    @Schema(description = "Destination ID (optional, for reference only)")
    private Long destinationId;
    
    // ✅ 方案 B: 添加目的地信息字段
    @Schema(description = "Destination name")
    private String destinationName;
    
    @Schema(description = "Destination country")
    private String destinationCountry;
    
    @Schema(description = "Destination latitude")
    private BigDecimal destinationLatitude;
    
    @Schema(description = "Destination longitude")
    private BigDecimal destinationLongitude;

    @Schema(description = "Start date (optional)")
    private LocalDate startDate;

    @Schema(description = "End date (optional)")
    private LocalDate endDate;

    private Integer durationDays;

    private Integer partySize;

    @Schema(description = "Budget in AUD")
    private BigDecimal totalBudget;

    private String currency;

    private String status;

    @Schema(description = "Current itinerary version")
    private Integer currentVersion;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Schema(description = "AI generated trip summary (markdown/plain text)")
    private String aiSummary;
}
