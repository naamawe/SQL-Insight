package com.xhx.core.service.auth;

import com.xhx.core.model.dto.LoginDTO;

/**
 * 认证服务接口
 * @author master
 */
public interface AuthService {
    /**
     * 用户登录
     * @param loginDto 登录参数
     * @return JWT Token
     */
    String login(LoginDTO loginDto);

    /**
     * 用户注册
     * @param registerDto 注册参数
     */
    void register(LoginDTO registerDto);

    /**
     * 退出登录
     */
    void logout();
}
