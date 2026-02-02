package com.travel.agent.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.travel.agent.entity.UserPreferences;
import com.travel.agent.mapper.UserPreferencesMapper;
import com.travel.agent.service.UserPreferencesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * Travel preferences 服务实现类
 * </p>
 *
 * @author Seren
 * @since 2025-10-18
 */
@Slf4j
@Service
public class UserPreferencesServiceImpl extends ServiceImpl<UserPreferencesMapper, UserPreferences> implements UserPreferencesService {

    /**
     * 根据用户ID查找旅行偏好
     */
    public UserPreferences findByUserId(Long userId) {
        return this.lambdaQuery()
                .eq(UserPreferences::getUserId, userId)
                .one();
    }

    /**
     * 创建或更新用户旅行偏好
     */
    public void createOrUpdate(Long userId, String travelStyle, List<String> interests, Byte budgetPreference) {
        UserPreferences existing = findByUserId(userId);
        String interestsJson = JSONUtil.toJsonStr(interests);
        
        if (existing != null) {
            // 更新已有偏好
            existing.setTravelStyle(travelStyle);
            existing.setInterests(interestsJson);
            existing.setBudgetPreference(budgetPreference);
            this.updateById(existing);
            log.info("Updated preferences for userId={}", userId);
        } else {
            // 创建新偏好
            UserPreferences newPreferences = new UserPreferences();
            newPreferences.setUserId(userId);
            newPreferences.setTravelStyle(travelStyle);
            newPreferences.setInterests(interestsJson);
            newPreferences.setBudgetPreference(budgetPreference);
            newPreferences.setCreatedAt(LocalDateTime.now());
            this.save(newPreferences);
            log.info("Created preferences for userId={}", userId);
        }
    }

    /**
     * 解析 interests JSON 字符串为列表
     */
    public List<String> parseInterests(String interestsJson) {
        if (interestsJson == null || interestsJson.isEmpty()) {
            return List.of();
        }
        try {
            return JSONUtil.toList(interestsJson, String.class);
        } catch (Exception e) {
            log.error("Failed to parse interests JSON: {}", interestsJson, e);
            return List.of();
        }
    }
}
