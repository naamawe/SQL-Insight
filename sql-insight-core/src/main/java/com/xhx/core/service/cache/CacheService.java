package com.xhx.core.service.cache;

import com.xhx.core.model.TableMetadata;

import java.util.List;
import java.util.Set;

/**
 * 统一缓存服务门面
 * 所有业务代码只允许通过此接口操作缓存，禁止直接操作 RedisTemplate
 * @author master
 */
public interface CacheService {

    // ===== Token =====
    void putToken(Long userId, String token);
    String getToken(Long userId);
    void evictToken(Long userId);
    Long getTokenExpireMinutes(Long userId);

    // ===== 用户系统权限（ROLE_ADMIN 等） =====
    void putUserSysPerm(Long userId, String perm);
    String getUserSysPerm(Long userId);
    void evictUserSysPerm(Long userId);

    // ===== 用户 RoleId =====
    void putUserRoleId(Long userId, Long roleId);
    Long getUserRoleId(Long userId);
    void evictUserRoleId(Long userId);

    // ===== 表权限集合 =====
    /**
     * 写入表权限集合
     * @param permissions 权限字符串集合，格式：{dsId}:{tableName}:SELECT
     *                    允许为空集合（空集合也会写入标记Key，区分"无权限"和"未加载"）
     */
    void putUserPermissions(Long userId, Set<String> permissions);

    /**
     * 获取表权限集合
     * @return null 表示缓存未命中（需要懒加载）；空集合表示确实无权限
     */
    Set<String> getUserPermissions(Long userId);
    void evictUserPermissions(Long userId);

    // ===== 查询策略 =====
    void putUserPolicy(Long userId, String policyJson);
    /** @return null 表示未命中或无策略 */
    String getUserPolicy(Long userId);
    void evictUserPolicy(Long userId);

    // ===== 用户可访问数据源ID列表 =====
    void putUserDsIds(Long userId, List<Long> dsIds);
    /** @return null 表示缓存未命中 */
    List<Long> getUserDsIds(Long userId);
    void evictUserDsIds(Long userId);

    // ===== 数据源表名列表 =====
    void putDsTables(Long dataSourceId, List<String> tables);
    /** @return null 表示缓存未命中 */
    List<String> getDsTables(Long dataSourceId);
    void evictDsTables(Long dataSourceId);

    // ===== Schema 元数据缓存（结构化，供 SchemaLinker 使用） =====
    /**
     * 写入结构化 Schema 元数据
     * @param dataSourceId 数据源ID
     * @param permHash     有序表名列表的 hashCode（十六进制），不同权限组合各自独立缓存
     * @param metadata     结构化元数据列表
     */
    void putSchemaMetadata(Long dataSourceId, String permHash, List<TableMetadata> metadata);

    /**
     * 读取结构化 Schema 元数据
     * @return null 表示缓存未命中
     */
    List<TableMetadata> getSchemaMetadata(Long dataSourceId, String permHash);

    /**
     * 失效某数据源下所有 Schema 缓存（表结构变更 / 数据源删除时调用）
     */
    void evictSchema(Long dataSourceId);

    // ===== 批量失效（登出/踢人） =====
    void evictAllUserCache(Long userId);

    // ===== 续期 =====
    void renewUserSession(Long userId);
}