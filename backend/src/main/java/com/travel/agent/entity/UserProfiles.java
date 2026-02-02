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
 * User profiles
 * </p>
 *
 * @author Seren
 * @since 2025-10-18
 */
@Getter
@Setter
@TableName("user_profiles")
@Schema(name = "UserProfiles", description = "User profiles")
public class UserProfiles implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "Profile ID")
    private Long id;

    @Schema(description = "User ID reference")
    private Long userId;

    @Schema(description = "Preferred language")
    private String language;

    @Schema(description = "Current city")
    private String location;

    @Schema(description = "Age range: 18-30, 31-50, etc.")
    private String ageRange;

    @Schema(description = "Created timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Updated timestamp")
    private LocalDateTime updatedAt;
}
