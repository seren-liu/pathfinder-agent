package com.travel.agent.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 认证响应（注册/登录）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "认证响应")
public class AuthResponse {

    @Schema(description = "用户ID", example = "1001")
    private Long userId;

    @Schema(description = "用户邮箱", example = "alice@example.com")
    private String email;

    @Schema(description = "JWT Token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String token;

    @Schema(description = "用户状态", example = "active")
    private String status;

    @Schema(description = "是否已完成个人资料设置", example = "false")
    private Boolean hasProfile;
}

