package com.xhx.common.constant;

/**
 * @author master
 */
public class SecurityConstants {

    // ==================== Token ====================
    /** login:token:{userId} */
    public static final String TOKEN_KEY = "login:token:";

    // ==================== 用户基础信息 ====================
    /** user:role_id:{userId}  →  String: roleId */
    public static final String USER_ROLE_ID_KEY = "user:role_id:";

    /** user:sys_perm:{userId}  →  String: ROLE_ADMIN 等 */
    public static final String USER_SYS_PERM_KEY = "user:sys_perm:";

    // ==================== 权限体系 ====================
    /** user:perm:mark:{userId}  →  String: "1"（标记权限已加载，解决空集合判断问题） */
    public static final String USER_PERM_MARK_KEY = "user:perm:mark:";

    /** user:perm:set:{userId}   →  Set: {dsId}:{tableName}:SELECT */
    public static final String USER_PERM_SET_KEY = "user:perm:set:";

    /** user:policy:{userId}     →  String: QueryPolicy JSON */
    public static final String USER_POLICY_KEY = "user:policy:";

    /** user:ds_ids:{userId}     →  String: [1,2,3] JSON */
    public static final String USER_DS_IDS_KEY = "user:ds_ids:";

    // ==================== 数据源元数据 ====================
    /** ds:tables:{dataSourceId} →  String: ["t1","t2"] JSON */
    public static final String DS_TABLES_KEY = "ds:tables:";

    // ==================== 分布式锁 ====================
    /** lock:perm:load:{userId} */
    public static final String LOCK_PERM_LOAD_KEY = "lock:perm:load:";

    // ==================== TTL ====================
    public static final long TOKEN_TTL_HOURS = 24;
    public static final long PERM_TTL_BASE_MINUTES = 1440;
    public static final long PERM_TTL_RANDOM_MINUTES = 60;
    public static final long DS_TABLES_TTL_MINUTES = 10;
    public static final long LOCK_TTL_SECONDS = 5;
    public static final long TOKEN_RENEW_THRESHOLD_MINUTES = 10;
}