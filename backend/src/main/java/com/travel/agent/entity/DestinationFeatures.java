package com.travel.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * Destination features
 * </p>
 *
 * @author Seren
 * @since 2025-10-18
 */
@Getter
@Setter
@TableName("destination_features")
@Schema(name = "DestinationFeatures", description = "Destination features")
public class DestinationFeatures implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "Feature ID")
    private Long id;

    @Schema(description = "Destination ID reference")
    private Long destinationId;

    @Schema(description = "Feature: beach/mountains/culture/urban")
    private String featureName;

    @Schema(description = "Created timestamp")
    private LocalDateTime createdAt;
}
