package com.xhx.core.service.cache.Impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.xhx.common.constant.SecurityConstants;
import com.xhx.common.model.TableMetadata;
import com.xhx.core.service.cache.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author master
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheServiceImpl implements CacheService {

    private final StringRedisTemplate redisTemplate;

    // ==================== Token ====================

    @Override
    public void putToken(Long userId, String token) {
        redisTemplate.opsForValue().set(
                SecurityConstants.TOKEN_KEY + userId,
                token,
                SecurityConstants.TOKEN_TTL_HOURS, TimeUnit.HOURS
        );
    }

    @Override
    public String getToken(Long userId) {
        return redisTemplate.opsForValue().get(SecurityConstants.TOKEN_KEY + userId);
    }

    @Override
    public void evictToken(Long userId) {
        redisTemplate.delete(SecurityConstants.TOKEN_KEY + userId);
    }

    @Override
    public Long getTokenExpireMinutes(Long userId) {
        return redisTemplate.getExpire(
                SecurityConstants.TOKEN_KEY + userId, TimeUnit.MINUTES);
    }

    // ==================== 系统权限 ====================

    @Override
    public void putUserSysPerm(Long userId, String perm) {
        redisTemplate.opsForValue().set(
                SecurityConstants.USER_SYS_PERM_KEY + userId,
                perm,
                SecurityConstants.TOKEN_TTL_HOURS, TimeUnit.HOURS
        );
    }

    @Override
    public String getUserSysPerm(Long userId) {
        return redisTemplate.opsForValue().get(SecurityConstants.USER_SYS_PERM_KEY + userId);
    }

    @Override
    public void evictUserSysPerm(Long userId) {
        redisTemplate.delete(SecurityConstants.USER_SYS_PERM_KEY + userId);
    }

    // ==================== RoleId ====================

    @Override
    public void putUserRoleId(Long userId, Long roleId) {
        redisTemplate.opsForValue().set(
                SecurityConstants.USER_ROLE_ID_KEY + userId,
                String.valueOf(roleId),
                SecurityConstants.TOKEN_TTL_HOURS, TimeUnit.HOURS
        );
    }

    @Override
    public Long getUserRoleId(Long userId) {
        String val = redisTemplate.opsForValue().get(SecurityConstants.USER_ROLE_ID_KEY + userId);
        return val == null ? null : Long.parseLong(val);
    }

    @Override
    public void evictUserRoleId(Long userId) {
        redisTemplate.delete(SecurityConstants.USER_ROLE_ID_KEY + userId);
    }

    // ==================== 表权限集合 ====================

    @Override
    public void putUserPermissions(Long userId, Set<String> permissions) {
        String setKey = SecurityConstants.USER_PERM_SET_KEY + userId;
        String markKey = SecurityConstants.USER_PERM_MARK_KEY + userId;
        long ttl = randomTtl();

        redisTemplate.delete(setKey);

        if (!permissions.isEmpty()) {
            redisTemplate.opsForSet().add(setKey, permissions.toArray(new String[0]));
            redisTemplate.expire(setKey, ttl, TimeUnit.MINUTES);
        }

        redisTemplate.opsForValue().set(markKey, "1", ttl, TimeUnit.MINUTES);
        log.debug("用户 {} 权限缓存已写入，权限数量: {}", userId, permissions.size());
    }

    @Override
    public Set<String> getUserPermissions(Long userId) {
        String markKey = SecurityConstants.USER_PERM_MARK_KEY + userId;

        if (Boolean.FALSE.equals(redisTemplate.hasKey(markKey))) {
            return null;
        }

        Set<String> members = redisTemplate.opsForSet()
                .members(SecurityConstants.USER_PERM_SET_KEY + userId);
        return members == null ? Collections.emptySet() : members;
    }

    @Override
    public void evictUserPermissions(Long userId) {
        redisTemplate.delete(SecurityConstants.USER_PERM_SET_KEY + userId);
        redisTemplate.delete(SecurityConstants.USER_PERM_MARK_KEY + userId);
    }

    // ==================== 查询策略 ====================

    @Override
    public void putUserPolicy(Long userId, String policyJson) {
        redisTemplate.opsForValue().set(
                SecurityConstants.USER_POLICY_KEY + userId,
                policyJson,
                randomTtl(), TimeUnit.MINUTES
        );
    }

    @Override
    public String getUserPolicy(Long userId) {
        return redisTemplate.opsForValue().get(SecurityConstants.USER_POLICY_KEY + userId);
    }

    @Override
    public void evictUserPolicy(Long userId) {
        redisTemplate.delete(SecurityConstants.USER_POLICY_KEY + userId);
    }

    // ==================== 数据源ID列表 ====================

    @Override
    public void putUserDsIds(Long userId, List<Long> dsIds) {
        redisTemplate.opsForValue().set(
                SecurityConstants.USER_DS_IDS_KEY + userId,
                JSON.toJSONString(dsIds),
                randomTtl(), TimeUnit.MINUTES
        );
    }

    @Override
    public List<Long> getUserDsIds(Long userId) {
        String json = redisTemplate.opsForValue()
                .get(SecurityConstants.USER_DS_IDS_KEY + userId);
        return json == null ? null : JSON.parseArray(json, Long.class);
    }

    @Override
    public void evictUserDsIds(Long userId) {
        redisTemplate.delete(SecurityConstants.USER_DS_IDS_KEY + userId);
    }

    // ==================== 数据源表名 ====================

    @Override
    public void putDsTables(Long dataSourceId, List<String> tables) {
        redisTemplate.opsForValue().set(
                SecurityConstants.DS_TABLES_KEY + dataSourceId,
                JSON.toJSONString(tables),
                SecurityConstants.DS_TABLES_TTL_MINUTES, TimeUnit.MINUTES
        );
        log.debug("数据源 {} 表名缓存已写入，表数量: {}", dataSourceId, tables.size());
    }

    @Override
    public List<String> getDsTables(Long dataSourceId) {
        String json = redisTemplate.opsForValue()
                .get(SecurityConstants.DS_TABLES_KEY + dataSourceId);
        return json == null ? null : JSON.parseArray(json, String.class);
    }

    @Override
    public void evictDsTables(Long dataSourceId) {
        redisTemplate.delete(SecurityConstants.DS_TABLES_KEY + dataSourceId);
    }

    // ==================== Schema 元数据缓存 ====================

    @Override
    public void putSchemaMetadata(Long dataSourceId, String permHash, List<TableMetadata> metadata) {
        String key = SecurityConstants.SCHEMA_KEY + dataSourceId + ":" + permHash;
        redisTemplate.opsForValue().set(
                key,
                JSON.toJSONString(metadata),
                SecurityConstants.SCHEMA_TTL_MINUTES, TimeUnit.MINUTES
        );
        log.debug("Schema 元数据缓存已写入，数据源: {}, hash: {}, 表数量: {}",
                dataSourceId, permHash, metadata.size());
    }

    @Override
    public List<TableMetadata> getSchemaMetadata(Long dataSourceId, String permHash) {
        String key = SecurityConstants.SCHEMA_KEY + dataSourceId + ":" + permHash;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        return JSON.parseObject(json, new TypeReference<>() {}
        );
    }

    @Override
    public void evictSchema(Long dataSourceId) {
        String pattern = SecurityConstants.SCHEMA_KEY + dataSourceId + ":*";
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("数据源 {} 的 Schema 缓存已全部失效，共删除 {} 个 key",
                    dataSourceId, keys.size());
        }
    }

    // ==================== 批量失效 ====================

    @Override
    public void evictAllUserCache(Long userId) {
        List<String> keys = Arrays.asList(
                SecurityConstants.TOKEN_KEY + userId,
                SecurityConstants.USER_SYS_PERM_KEY + userId,
                SecurityConstants.USER_ROLE_ID_KEY + userId,
                SecurityConstants.USER_PERM_MARK_KEY + userId,
                SecurityConstants.USER_PERM_SET_KEY + userId,
                SecurityConstants.USER_POLICY_KEY + userId,
                SecurityConstants.USER_DS_IDS_KEY + userId
        );
        redisTemplate.delete(keys);
        log.info("用户 {} 全量缓存已清除", userId);
    }

    // ==================== 续期 ====================

    @Override
    public void renewUserSession(Long userId) {
        List<String> sessionKeys = Arrays.asList(
                SecurityConstants.TOKEN_KEY + userId,
                SecurityConstants.USER_SYS_PERM_KEY + userId,
                SecurityConstants.USER_ROLE_ID_KEY + userId
        );
        sessionKeys.forEach(key ->
                redisTemplate.expire(key, SecurityConstants.TOKEN_TTL_HOURS, TimeUnit.HOURS));
    }

    // ==================== 私有工具 ====================

    private long randomTtl() {
        return SecurityConstants.PERM_TTL_BASE_MINUTES
                + (long) (Math.random() * SecurityConstants.PERM_TTL_RANDOM_MINUTES);
    }
}