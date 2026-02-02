package com.travel.agent.controller;

import cn.hutool.json.JSONUtil;
import com.travel.agent.dto.request.ProfileSetupRequest;
import com.travel.agent.dto.response.CommonResponse;
import com.travel.agent.dto.response.UserDetailResponse;
import com.travel.agent.entity.UserPreferences;
import com.travel.agent.entity.UserProfiles;
import com.travel.agent.entity.Users;
import com.travel.agent.exception.BusinessException;
import com.travel.agent.service.UserPreferencesService;
import com.travel.agent.service.UserProfilesService;
import com.travel.agent.service.UsersService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * <p>
 * User profiles 前端控制器
 * </p>
 *
 * @author Seren
 * @since 2025-10-18
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User Profile", description = "用户个人资料接口")
public class UserProfilesController {

    private final UsersService usersService;
    private final UserProfilesService userProfilesService;
    private final UserPreferencesService userPreferencesService;

    /**
     * 设置用户个人资料（包含 profile + preferences）
     */
    @PostMapping("/{userId}/profile")
    @Operation(summary = "设置用户个人资料", description = "完善用户个人信息和旅行偏好")
    public CommonResponse<UserDetailResponse> setupProfile(
            @PathVariable Long userId,
            @Valid @RequestBody ProfileSetupRequest request) {
        
        log.info("Setup profile request: userId={}, data={}", userId, request);

        // 1. 验证用户是否存在
        Users user = usersService.getById(userId);
        if (user == null) {
            throw new BusinessException("User not found");
        }

        // 2. 创建或更新 UserProfiles
        UserProfiles existingProfile = userProfilesService.lambdaQuery()
                .eq(UserProfiles::getUserId, userId)
                .one();

        if (existingProfile != null) {
            // 更新已有资料
            existingProfile.setLanguage(request.getLanguage());
            existingProfile.setLocation(request.getLocation());
            existingProfile.setAgeRange(request.getAgeRange());
            existingProfile.setUpdatedAt(LocalDateTime.now());
            userProfilesService.updateById(existingProfile);
            log.info("Updated existing profile: userId={}", userId);
        } else {
            // 创建新资料
            UserProfiles newProfile = new UserProfiles();
            newProfile.setUserId(userId);
            newProfile.setLanguage(request.getLanguage());
            newProfile.setLocation(request.getLocation());
            newProfile.setAgeRange(request.getAgeRange());
            newProfile.setCreatedAt(LocalDateTime.now());
            newProfile.setUpdatedAt(LocalDateTime.now());
            userProfilesService.save(newProfile);
            log.info("Created new profile: userId={}", userId);
        }

        // 3. 创建或更新 UserPreferences
        UserPreferences existingPreferences = userPreferencesService.lambdaQuery()
                .eq(UserPreferences::getUserId, userId)
                .one();

        String interestsJson = JSONUtil.toJsonStr(request.getInterests());

        if (existingPreferences != null) {
            // 更新已有偏好
            existingPreferences.setTravelStyle(request.getTravelStyle());
            existingPreferences.setInterests(interestsJson);
            existingPreferences.setBudgetPreference(request.getBudgetPreference());
            userPreferencesService.updateById(existingPreferences);
            log.info("Updated existing preferences: userId={}", userId);
        } else {
            // 创建新偏好
            UserPreferences newPreferences = new UserPreferences();
            newPreferences.setUserId(userId);
            newPreferences.setTravelStyle(request.getTravelStyle());
            newPreferences.setInterests(interestsJson);
            newPreferences.setBudgetPreference(request.getBudgetPreference());
            newPreferences.setCreatedAt(LocalDateTime.now());
            userPreferencesService.save(newPreferences);
            log.info("Created new preferences: userId={}", userId);
        }

        // 4. 查询完整的用户信息并返回
        return getUserDetail(userId);
    }

    /**
     * 获取用户详细信息
     * 使用 Redis 缓存，缓存 key 为 "userProfile::userId"
     */
    @GetMapping("/{userId}/profile")
    @Operation(summary = "获取用户详细信息", description = "获取用户的完整个人资料和偏好")
    @Cacheable(value = "userProfile", key = "#userId", unless = "#result == null || #result.data == null")
    public CommonResponse<UserDetailResponse> getUserDetail(@PathVariable Long userId) {
        
        log.info("Get user detail from database: userId={}", userId);

        // 1. 查询用户基本信息
        Users user = usersService.getById(userId);
        if (user == null) {
            throw new BusinessException("User not found");
        }

        // 2. 查询 Profile
        UserProfiles profile = userProfilesService.lambdaQuery()
                .eq(UserProfiles::getUserId, userId)
                .one();

        // 3. 查询 Preferences
        UserPreferences preferences = userPreferencesService.lambdaQuery()
                .eq(UserPreferences::getUserId, userId)
                .one();

        // 4. 解析 interests JSON
        List<String> interests = null;
        if (preferences != null && preferences.getInterests() != null) {
            interests = JSONUtil.toList(preferences.getInterests(), String.class);
        }

        // 5. 构建响应
        UserDetailResponse response = UserDetailResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .status(user.getStatus())
                .language(profile != null ? profile.getLanguage() : null)
                .location(profile != null ? profile.getLocation() : null)
                .ageRange(profile != null ? profile.getAgeRange() : null)
                .travelStyle(preferences != null ? preferences.getTravelStyle() : null)
                .interests(interests)
                .budgetPreference(preferences != null ? preferences.getBudgetPreference() : null)
                .createdAt(user.getCreatedAt() != null ? 
                    user.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null)
                .build();

        return CommonResponse.success(response);
    }

    /**
     * 获取当前登录用户信息（简化版：通过请求头获取 userId）
     */
    @GetMapping("/me")
    @Operation(summary = "获取当前用户信息", description = "获取当前登录用户的详细信息")
    public CommonResponse<UserDetailResponse> getCurrentUser(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        
        if (userId == null) {
            throw new BusinessException("User not authenticated");
        }

        return getUserDetail(userId);
    }
}
