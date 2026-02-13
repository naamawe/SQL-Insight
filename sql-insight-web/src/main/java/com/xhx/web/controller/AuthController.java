package com.xhx.web.controller;

import com.xhx.common.result.Result;
import com.xhx.core.model.dto.LoginDTO;
import com.xhx.core.service.auth.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 认证授权控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 登录接口
     * @param loginDto 包含用户名和密码的 JSON
     * @return 包含 Token 的统一结果集
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
     * @return 成功提示
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        
        log.info("退出登录成功");
        return Result.success("退出登录成功", null);
    }

    /**
     * 注册接口
     * @param registerDto 包含用户名和密码的 JSON
     * @return 成功提示
     */
    @PostMapping("/register")
    public Result<Void> register(@RequestBody LoginDTO registerDto) {
        if (registerDto.getUsername().length() < 4) {
            return Result.error("用户名长度不能少于4位");
        }

        authService.register(registerDto);
        return Result.success("注册成功", null);
    }

}
