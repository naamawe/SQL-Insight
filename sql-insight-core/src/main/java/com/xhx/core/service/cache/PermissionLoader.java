package com.xhx.core.service.cache;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xhx.common.constant.SecurityConstants;
import com.xhx.dal.entity.QueryPolicy;
import com.xhx.dal.entity.TablePermission;
import com.xhx.dal.mapper.QueryPolicyMapper;
import com.xhx.dal.mapper.TablePermissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 权限懒加载器
 * 负责"缓存未命中时从DB加载并回填"的完整流程
 * 使用分布式锁防止缓存击穿（同一用户并发请求同时miss时只有一个线程打DB）
 * @author master
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionLoader {

    private final CacheService cacheService;
    private final TablePermissionMapper tablePermissionMapper;
    private final QueryPolicyMapper queryPolicyMapper;
    private final StringRedisTemplate redisTemplate;

    private static final String NO_POLICY_SENTINEL = "NO_POLICY";

    /**
     * 获取用户表权限集合（Cache-Aside完整流程）
     *
     * @return 永远不返回 null，缓存miss时会从DB加载
     */
    public Set<String> loadPermissions(Long userId, Long roleId) {
        // 第一次检查（无锁，绝大多数请求在这里命中，走快路径）
        Set<String> cached = cacheService.getUserPermissions(userId);
        if (cached != null) {
            return cached;
        }

        // 缓存未命中，抢分布式锁
        String lockKey = SecurityConstants.LOCK_PERM_LOAD_KEY + userId;
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1",
                        SecurityConstants.LOCK_TTL_SECONDS, TimeUnit.SECONDS);

        if (Boolean.TRUE.equals(locked)) {
            try {
                // 第二次检查（持锁后再查一次，防止排队中已被其他线程回填）
                cached = cacheService.getUserPermissions(userId);
                if (cached != null) {
                    return cached;
                }
                // 真正从DB加载
                return doLoadFromDb(userId, roleId);
            } finally {
                redisTemplate.delete(lockKey);
            }
        } else {
            // 没抢到锁，说明其他线程正在加载，等待后重试
            log.debug("用户 {} 权限正在被其他线程加载，等待重试", userId);
            try {
                Thread.sleep(80);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // 重试一次，如果还是null说明加载失败，返回空集合（安全降级）
            cached = cacheService.getUserPermissions(userId);
            return cached != null ? cached : Collections.emptySet();
        }
    }

    /**
     * 获取用户查询策略（Cache-Aside，策略相对简单，不需要加锁）
     */
    public String loadPolicy(Long userId, Long roleId) {
        String cached = cacheService.getUserPolicy(userId);
        if (cached != null) {
            return cached;
        }

        QueryPolicy policy = queryPolicyMapper.selectOne(
                new LambdaQueryWrapper<QueryPolicy>()
                        .eq(QueryPolicy::getRoleId, roleId)
        );

        if (policy != null) {
            String json = JSON.toJSONString(policy);
            cacheService.putUserPolicy(userId, json);
            return json;
        }

        cacheService.putUserPolicy(userId, NO_POLICY_SENTINEL);
        return null;
    }

    /**
     * 从DB加载权限并回填缓存（被 loadPermissions 内部调用）
     */
    public Set<String> doLoadFromDb(Long userId, Long roleId) {
        log.info("用户 {} 权限缓存未命中，从DB加载（roleId: {}）", userId, roleId);

        List<TablePermission> perms = tablePermissionMapper.selectList(
                new LambdaQueryWrapper<TablePermission>()
                        .eq(TablePermission::getRoleId, roleId)
        );

        Set<String> permSet = perms.stream()
                .map(p -> p.getDataSourceId() + ":" + p.getTableName() + ":" + p.getPermission())
                .collect(Collectors.toSet());

        // 同时加载策略
        QueryPolicy policy = queryPolicyMapper.selectOne(
                new LambdaQueryWrapper<QueryPolicy>()
                        .eq(QueryPolicy::getRoleId, roleId)
        );

        // 回填缓存（权限和策略一起写，保持一致性）
        cacheService.putUserPermissions(userId, permSet);
        if (policy != null) {
            cacheService.putUserPolicy(userId, JSON.toJSONString(policy));
        }

        return permSet;
    }

    /**
     * 失效用户权限相关缓存（变更时调用，只删不写）
     */
    public void evict(Long userId) {
        cacheService.evictUserPermissions(userId);
        cacheService.evictUserPolicy(userId);
        log.debug("用户 {} 权限缓存已失效", userId);
    }
}