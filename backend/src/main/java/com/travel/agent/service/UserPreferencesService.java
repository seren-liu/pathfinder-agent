package com.travel.agent.service;

import com.travel.agent.entity.UserPreferences;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 * Travel preferences 服务类
 * </p>
 *
 * @author Seren
 * @since 2025-10-18
 */
public interface UserPreferencesService extends IService<UserPreferences> {

    /**
     * 根据用户ID查询偏好
     * @param userId 用户ID
     * @return 用户偏好对象
     */
    UserPreferences findByUserId(Long userId);
    
    /**
     * 解析 JSON 格式的兴趣列表
     * @param interestsJson JSON 字符串，例如: ["beach", "hiking"]
     * @return 兴趣列表
     */
    List<String> parseInterests(String interestsJson);
    
    /**
     * 创建或更新用户旅行偏好
     */
    void createOrUpdate(Long userId, String travelStyle, List<String> interests, Byte budgetPreference);
}
