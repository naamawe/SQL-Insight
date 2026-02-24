package com.xhx.web.exception;

import com.xhx.common.exception.BaseException;
import com.xhx.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Objects;

/**
 * 全局异常处理器
 * @author master
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 捕获所有继承自 BaseException 的自定义业务异常
     */
    @ExceptionHandler(BaseException.class)
    public Result<String> handleBaseException(BaseException e) {
        log.warn("业务逻辑异常 [{}]: {}", e.getClass().getSimpleName(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 捕获 @Valid 校验失败异常，提取第一条错误信息返回
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleValidException(MethodArgumentNotValidException e) {
        String message = Objects.requireNonNull(e.getBindingResult().getFieldError()).getDefaultMessage();

        log.warn("参数校验失败: {}", message);
        return Result.error(400, message);
    }

    /**
     * 捕获 Spring Security 认证失败异常（用户名或密码错误）
     */
    @ExceptionHandler(BadCredentialsException.class)
    public Result<Void> handleBadCredentialsException(BadCredentialsException e) {
        log.warn("登录失败: {}", e.getMessage());
        return Result.error(401, "用户名或密码错误");
    }

    /**
     * 捕获 Spring Security 认证异常（401）
     * 例如 Token 过期、未登录等由 Security 框架抛出的场景
     */
    @ExceptionHandler(AuthenticationException.class)
    public Result<String> handleAuthException(AuthenticationException e) {
        log.warn("认证失败: {}", e.getMessage());
        return Result.error(401, "登录失效，请重新登录");
    }

    /**
     * 捕获 Spring Security 权限不足异常（403）
     */
    @ExceptionHandler(AccessDeniedException.class)
    public Result<String> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("权限不足: {}", e.getMessage());
        return Result.error(403, "您没有操作权限");
    }

    /**
     * 兜底：处理所有未知异常（500）
     */
    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        log.error("系统未知异常", e);
        return Result.error(500, "系统繁忙，请稍后再试");
    }
}