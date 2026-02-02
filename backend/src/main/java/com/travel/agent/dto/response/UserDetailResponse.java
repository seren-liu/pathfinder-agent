package com.travel.agent.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户详情响应（包含 profile + preferences）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户详情响应")
public class UserDetailResponse {

    // === User 基本信息 ===
    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "状态")
    private String status;

    // === Profile 信息 ===
    @Schema(description = "语言偏好")
    private String language;

    @Schema(description = "当前位置")
    private String location;

    @Schema(description = "年龄段")
    private String ageRange;

    // === Preferences 信息 ===
    @Schema(description = "旅行风格")
    private String travelStyle;

    @Schema(description = "兴趣标签")
    private List<String> interests;

    @Schema(description = "预算偏好")
    private Byte budgetPreference;

    @Schema(description = "账号创建时间")
    private String createdAt;
}

