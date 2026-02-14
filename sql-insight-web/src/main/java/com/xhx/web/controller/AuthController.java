package com.xhx.web.controller;

import com.xhx.common.context.UserContext;
import com.xhx.common.result.Result;
import com.xhx.core.model.dto.LoginDTO;
import com.xhx.core.service.auth.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证授权控制器
 * @author master
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 登录接口
     */
    @PostMapping("/login")
    public Result<String> login(@RequestBody LoginDTO loginDto) {
        log.info("接收到登录请求: 用户名 = {}", loginDto.getUsername());
        String token = authService.login(loginDto);
        log.info("用户 {} 登录成功，返回令牌", loginDto.getUsername());
        return Result.success("登录成功", token);
    }

    /**
     * 退出登录接口
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        log.info("用户{}退出登录成功", UserContext.getUserId());
        return Result.success("退出登录成功", null);
    }

    /**
     * 注册接口
     */
    @PostMapping("/register")
    public Result<Void> register(@RequestBody LoginDTO registerDto) {
        if (registerDto.getUsername().length() < 4) {
            return Result.error("用户名长度不能少于4位");
        }
        authService.register(registerDto);
        return Result.success("注册成功", null);
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/me")
    public Result<Map<String, Object>> getCurrentUser() {
        Map<String, Object> userInfo = authService.getCurrentUserInfo();
        return Result.success(userInfo);
    }
}