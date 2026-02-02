package com.travel.agent.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.travel.agent.entity.Users;
import com.travel.agent.mapper.UsersMapper;
import com.travel.agent.service.UsersService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * <p>
 * User accounts 服务实现类
 * </p>
 *
 * @author Seren
 * @since 2025-10-18
 */
@Slf4j
@Service
public class UsersServiceImpl extends ServiceImpl<UsersMapper, Users> implements UsersService {

    /**
     * 根据邮箱查找用户
     */
    public Users findByEmail(String email) {
        return this.lambdaQuery()
                .eq(Users::getEmail, email)
                .one();
    }

    /**
     * 检查邮箱是否已存在
     */
    public boolean emailExists(String email) {
        return this.lambdaQuery()
                .eq(Users::getEmail, email)
                .count() > 0;
    }

    /**
     * 验证用户密码（简化版：明文对比）
     * 注意：生产环境应使用 BCrypt 加密对比
     */
    public boolean validatePassword(Users user, String rawPassword) {
        if (user == null || rawPassword == null) {
            return false;
        }
        // 简化版：明文对比
        // 生产环境应使用：passwordEncoder.matches(rawPassword, user.getPassword())
        return rawPassword.equals(user.getPassword());
    }

    /**
     * 检查用户状态是否有效
     */
    public boolean isUserActive(Users user) {
        return user != null && "active".equals(user.getStatus());
    }
}
