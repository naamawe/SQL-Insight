package com.xhx.web.controller;

import com.xhx.common.context.UserContext;
import com.xhx.common.result.Result;
import com.xhx.core.model.dto.LoginDTO;
import com.xhx.core.service.auth.AuthService;
import jakarta.validation.Valid;
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
     * 注意：登录不加 @Valid，避免旧账号因密码规则变更无法登录
     */
    @PostMapping("/login")
    public Result<String> login(@RequestBody LoginDTO loginDto) {
        log.info("接收到登录请求: 用户名 = {}", loginDto.getUsername());
        String token = authService.login(loginDto);
        return Result.success("登录成功", token);
    }

    /**
     * 退出登录接口
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        Long userId = UserContext.getUserId();
        authService.logout();
        log.info("用户{}退出登录成功", userId);
        return Result.success("退出登录成功", null);
    }

    /**
     * 注册接口
     * 加 @Valid 触发 LoginDTO 上的密码强度校验
     */
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody LoginDTO registerDto) {
        authService.register(registerDto);
        return Result.success("注册成功", null);
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/me")
    public Result<Map<String, Object>> getCurrentUser() {
        return Result.success(authService.getCurrentUserInfo());
    }
}