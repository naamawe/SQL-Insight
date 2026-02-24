package com.xhx.core.service.auth;

import com.xhx.core.model.dto.LoginDTO;
import java.util.Map;

/**
 * 认证服务接口
 * @author master
 */
public interface AuthService {
    /**
     * 用户登录
     */
    String login(LoginDTO loginDto);

    /**
     * 用户注册
     */
    void register(LoginDTO registerDto);

    /**
     * 退出登录
     */
    void logout();

    /**
     * 获取当前登录用户信息
     */
    Map<String, Object> getCurrentUserInfo();
}