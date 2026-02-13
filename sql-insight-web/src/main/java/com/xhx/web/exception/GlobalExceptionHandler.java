package com.xhx.web.exception;

import com.xhx.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.security.sasl.AuthenticationException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Spring Security 认证异常 (401)
    @ExceptionHandler(AuthenticationException.class)
    public Result<String> handleAuthException(AuthenticationException e) {
        log.warn("认证失败: {}", e.getMessage());
        return Result.error(401, "登录失效，请重新登录");
    }

    // 处理 Spring Security 权限异常 (403)
    @ExceptionHandler(AccessDeniedException.class)
    public Result<String> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("权限不足: {}", e.getMessage());
        return Result.error(403, "您没有操作权限");
    }

    // 处理其他所有未知异常 (500)
    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        log.error("系统未知异常", e);
        return Result.error(500, "系统繁忙，请稍后再试");
    }
}
