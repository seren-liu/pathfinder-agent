package com.travel.agent.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.travel.agent.entity.UserProfiles;
import com.travel.agent.mapper.UserProfilesMapper;
import com.travel.agent.service.UserProfilesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * <p>
 * User profiles 服务实现类
 * </p>
 *
 * @author Seren
 * @since 2025-10-18
 */
@Slf4j
@Service
public class UserProfilesServiceImpl extends ServiceImpl<UserProfilesMapper, UserProfiles> implements UserProfilesService {

    /**
     * 根据用户ID查找个人资料
     */
    public UserProfiles findByUserId(Long userId) {
        return this.lambdaQuery()
                .eq(UserProfiles::getUserId, userId)
                .one();
    }

    /**
     * 检查用户是否已设置个人资料
     */
    public boolean hasProfile(Long userId) {
        return this.lambdaQuery()
                .eq(UserProfiles::getUserId, userId)
                .count() > 0;
    }

    /**
     * 创建或更新用户个人资料
     */
    public void createOrUpdate(Long userId, String language, String location, String ageRange) {
        UserProfiles existing = findByUserId(userId);
        
        if (existing != null) {
            // 更新已有资料
            existing.setLanguage(language);
            existing.setLocation(location);
            existing.setAgeRange(ageRange);
            existing.setUpdatedAt(LocalDateTime.now());
            this.updateById(existing);
            log.info("Updated profile for userId={}", userId);
        } else {
            // 创建新资料
            UserProfiles newProfile = new UserProfiles();
            newProfile.setUserId(userId);
            newProfile.setLanguage(language);
            newProfile.setLocation(location);
            newProfile.setAgeRange(ageRange);
            newProfile.setCreatedAt(LocalDateTime.now());
            newProfile.setUpdatedAt(LocalDateTime.now());
            this.save(newProfile);
            log.info("Created profile for userId={}", userId);
        }
    }
}
