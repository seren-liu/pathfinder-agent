package com.travel.agent.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 个人资料设置请求（包含 profile + preferences）
 */
@Data
@Schema(description = "个人资料设置请求")
public class ProfileSetupRequest {

    // === User Profile 字段 ===
    
    @Schema(description = "语言偏好", example = "en", defaultValue = "en")
    private String language = "en";

    @NotBlank(message = "Location cannot be empty")
    @Schema(description = "当前位置", example = "Sydney")
    private String location;

    @NotBlank(message = "Age range cannot be empty")
    @Schema(description = "年龄段", example = "18-30")
    private String ageRange;

    // === User Preferences 字段 ===
    
    @NotBlank(message = "Travel style cannot be empty")
    @Schema(description = "旅行风格", example = "solo", allowableValues = {"family", "solo", "couple", "business"})
    private String travelStyle;

    @NotNull(message = "Interests cannot be null")
    @Schema(description = "兴趣标签", example = "[\"beach\", \"hiking\"]")
    private List<String> interests;

    @NotNull(message = "Budget preference cannot be null")
    @Schema(description = "预算偏好 (1=低, 2=中, 3=高)", example = "2")
    private Byte budgetPreference;
}

