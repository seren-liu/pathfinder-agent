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
 * Travel preferences
 * </p>
 *
 * @author Seren
 * @since 2025-10-18
 */
@Getter
@Setter
@TableName("user_preferences")
@Schema(name = "UserPreferences", description = "Travel preferences")
public class UserPreferences implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "Preference ID")
    private Long id;

    @Schema(description = "User ID reference")
    private Long userId;

    @Schema(description = "Travel style: family/solo/couple/business")
    private String travelStyle;

    @Schema(description = "Interest tags JSON: [\"beach\", \"culture\", \"food\"]")
    private String interests;

    @Schema(description = "Budget level: 1=low, 2=medium, 3=high")
    private Byte budgetPreference;

    @Schema(description = "Created timestamp")
    private LocalDateTime createdAt;
}
