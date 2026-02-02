package com.travel.agent.controller;

import com.travel.agent.dto.request.RegisterRequest;
import com.travel.agent.dto.response.AuthResponse;
import com.travel.agent.dto.response.CommonResponse;
import com.travel.agent.entity.Users;
import com.travel.agent.exception.BusinessException;
import com.travel.agent.service.UsersService;
import com.travel.agent.service.UserProfilesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * <p>
 * User accounts 前端控制器
 * </p>
 *
 * @author Seren
 * @since 2025-10-18
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "用户认证接口")
public class UsersController {

    private final UsersService usersService;
    private final UserProfilesService userProfilesService;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "创建新用户账号")
    public CommonResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Register request received: email={}", request.getEmail());

        // 1. 检查邮箱是否已存在
        Users existingUser = usersService.lambdaQuery()
                .eq(Users::getEmail, request.getEmail())
                .one();

        if (existingUser != null) {
            throw new BusinessException("Email already exists");
        }

        // 2. 创建用户（简化版：明文密码，实际项目应该加密）
        Users newUser = new Users();
        newUser.setEmail(request.getEmail());
        newUser.setPassword(request.getPassword()); // 注意：生产环境应使用 BCrypt 加密
        newUser.setStatus("active");
        newUser.setCreatedAt(LocalDateTime.now());
        newUser.setUpdatedAt(LocalDateTime.now());

        boolean saved = usersService.save(newUser);
        if (!saved) {
            throw new BusinessException("Failed to create user");
        }

        // 3. 生成简单的 Token（简化版：使用 UUID，实际项目应使用 JWT）
        String token = "token_" + UUID.randomUUID().toString().replace("-", "");

        // 4. 检查用户是否已设置个人资料
        Long profileCount = userProfilesService.lambdaQuery()
                .eq(com.travel.agent.entity.UserProfiles::getUserId, newUser.getId())
                .count();

        // 5. 构建响应
        AuthResponse response = AuthResponse.builder()
                .userId(newUser.getId())
                .email(newUser.getEmail())
                .token(token)
                .status(newUser.getStatus())
                .hasProfile(profileCount > 0)
                .build();

        log.info("User registered successfully: userId={}", newUser.getId());
        return CommonResponse.success(response);
    }

    /**
     * 用户登录（简化版）
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "使用邮箱密码登录")
    public CommonResponse<AuthResponse> login(@Valid @RequestBody RegisterRequest request) {
        log.info("Login request received: email={}", request.getEmail());

        // 1. 查找用户
        Users user = usersService.lambdaQuery()
                .eq(Users::getEmail, request.getEmail())
                .one();

        if (user == null) {
            throw new BusinessException("User not found");
        }

        // 2. 验证密码（简化版：明文对比）
        if (!user.getPassword().equals(request.getPassword())) {
            throw new BusinessException("Invalid password");
        }

        // 3. 检查账号状态
        if (!"active".equals(user.getStatus())) {
            throw new BusinessException("Account is inactive");
        }

        // 4. 生成 Token
        String token = "token_" + UUID.randomUUID().toString().replace("-", "");

        // 5. 检查是否已设置个人资料
        Long profileCount = userProfilesService.lambdaQuery()
                .eq(com.travel.agent.entity.UserProfiles::getUserId, user.getId())
                .count();

        // 6. 构建响应
        AuthResponse response = AuthResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .token(token)
                .status(user.getStatus())
                .hasProfile(profileCount > 0)
                .build();

        log.info("User logged in successfully: userId={}", user.getId());
        return CommonResponse.success(response);
    }

    /**
     * 退出登录（简化版：前端清除 token 即可）
     */
    @PostMapping("/logout")
    @Operation(summary = "用户退出", description = "退出登录")
    public CommonResponse<Void> logout() {
        // 简化版：实际应该清除 session 或使 JWT token 失效
        return CommonResponse.success("Logged out successfully", null);
    }
}
