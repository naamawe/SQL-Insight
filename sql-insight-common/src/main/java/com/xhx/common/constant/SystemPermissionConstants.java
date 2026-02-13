package com.xhx.common.constant;

/**
 * 系统功能权限常量
 */
public class SystemPermissionConstants {

    /** 超级管理员：拥有系统所有权限（包括权限分配、系统配置等） */
    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    /** 管理员：拥有日常管理权限（用户管理、数据源管理、查看日志等） */
    public static final String ADMIN = "ADMIN";

    /** 普通用户：仅拥有 Text-to-SQL 对话和查看自己历史记录的权限 */
    public static final String USER = "USER";
}
