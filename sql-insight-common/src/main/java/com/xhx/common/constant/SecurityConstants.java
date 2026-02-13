package com.xhx.common.constant;

/**
 * 安全相关常量
 * @author master
 */
public class SecurityConstants {
    /** Token 前缀 */
    public static final String TOKEN_PREFIX = "Bearer ";
    
    /** 认证请求头 */
    public static final String AUTHORIZATION_HEADER = "Authorization";

    /** Redis 存储 Token 的 Key 前缀 (login:token:userId) */
    public static final String REDIS_TOKEN_KEY = "login:token:";

    /** Token 有效期（单位：分钟）- 建议 30 分钟，配合续期逻辑 */
    public static final long TOKEN_EXPIRE_MINUTES = 30;

    /** 续期阈值（单位：分钟）- 当剩余时间少于 10 分钟时，触发自动续期 */
    public static final long TOKEN_RENEW_THRESHOLD = 10;

    /** 存放用户表权限的 Redis Key (Set类型: datasourceId:tableName:permission) */
    public static final String USER_PERMISSION_KEY = "user:permissions:";

    /** 存放用户查询策略的 Redis Key (Value类型: JSON字符串) */
    public static final String USER_POLICY_KEY = "user:policy:";

    /** 存放用户系统权限的 Redis Key (Set类型: permission) */
    public static final String USER_SYS_PERM_KEY = "user:sys_perm:";

    /** 存放用户已授权数据源 ID 的 Redis Key (List/Set类型) */
    public static final String USER_DATASOURCES_KEY = "user:datasources:";
}
