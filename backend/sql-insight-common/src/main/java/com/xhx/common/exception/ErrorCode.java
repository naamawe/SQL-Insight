package com.xhx.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 统一错误码枚举
 * @author master
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ========== 通用错误 (1xxx) ==========
    SUCCESS(200, "操作成功"),
    SYSTEM_ERROR(500, "系统错误"),
    INVALID_ARGUMENT(400, "参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "内部错误"),

    // ========== 认证授权错误 (2xxx) ==========
    TOKEN_INVALID(2001, "无效的令牌"),
    TOKEN_EXPIRED(2002, "令牌已过期"),
    PERMISSION_DENIED(2003, "权限不足"),
    LOGIN_REQUIRED(2004, "请先登录"),
    USER_DISABLED(2005, "用户已被禁用"),

    // ========== 数据源相关错误 (3xxx) ==========
    DATASOURCE_NOT_FOUND(3001, "数据源不存在"),
    DATASOURCE_CONNECTION_FAILED(3002, "数据库连接失败"),
    DATASOURCE_PASSWORD_ERROR(3003, "数据库密码错误"),
    DATASOURCE_ALREADY_EXISTS(3004, "数据源已存在"),
    DATASOURCE_CONFIG_ERROR(3005, "数据源配置错误"),

    // ========== SQL 执行相关错误 (4xxx) ==========
    SQL_SYNTAX_ERROR(4001, "SQL 语法错误"),
    SQL_EXECUTION_FAILED(4002, "SQL 执行失败"),
    SQL_SECURITY_VIOLATION(4003, "SQL 安全校验失败"),
    SQL_NOT_ALLOWED(4004, "当前角色不允许执行此 SQL"),
    SQL_TOO_COMPLEX(4005, "SQL 过于复杂"),
    SQL_GENERATION_FAILED(4006, "SQL 生成失败"),

    // ========== 会话相关错误 (5xxx) ==========
    SESSION_NOT_FOUND(5001, "会话不存在"),
    SESSION_EXPIRED(5002, "会话已过期"),
    RECORD_NOT_FOUND(5003, "对话记录不存在"),

    // ========== AI 相关错误 (6xxx) ==========
    AI_SERVICE_UNAVAILABLE(6001, "AI 服务不可用"),
    AI_TIMEOUT(6002, "AI 响应超时"),
    AI_RATE_LIMITED(6003, "AI 请求频率超限"),
    AI_GENERATION_FAILED(6004, "AI 生成失败"),
    SCHEMA_LINKING_FAILED(6005, "表结构匹配失败"),
    EMBEDDING_FAILED(6006, "语义向量化失败"),

    // ========== 图表相关错误 (7xxx) ==========
    CHART_CONFIG_ERROR(7001, "图表配置错误"),
    CHART_DATA_INVALID(7002, "图表数据无效"),
    CHART_NOT_FOUND(7003, "图表配置不存在"),

    // ========== 权限相关错误 (8xxx) ==========
    ROLE_NOT_FOUND(8001, "角色不存在"),
    ROLE_ALREADY_EXISTS(8002, "角色已存在"),
    PERMISSION_NOT_FOUND(8003, "权限不存在"),
    USER_ALREADY_EXISTS(8004, "用户已存在");

    private final int code;
    private final String message;

    /**
     * 根据错误码创建 ServiceException
     */
    public ServiceException toException() {
        return new ServiceException(this.code, this.message);
    }

    /**
     * 根据错误码创建 ServiceException，带自定义消息
     */
    public ServiceException toException(String detail) {
        return new ServiceException(this.code, this.message + ": " + detail);
    }
}