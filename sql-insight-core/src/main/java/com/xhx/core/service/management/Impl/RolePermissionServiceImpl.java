package com.xhx.core.service.management.Impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhx.common.constant.SecurityConstants;
import com.xhx.core.service.management.RolePermissionService;
import com.xhx.dal.entity.QueryPolicy;
import com.xhx.dal.entity.TablePermission;
import com.xhx.dal.entity.User;
import com.xhx.dal.mapper.QueryPolicyMapper;
import com.xhx.dal.mapper.TablePermissionMapper;
import com.xhx.dal.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 角色权限服务 - 最终完善版
 * @author master
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RolePermissionServiceImpl extends ServiceImpl<TablePermissionMapper, TablePermission> implements RolePermissionService {

    private final UserMapper userMapper;
    private final QueryPolicyMapper queryPolicyMapper;
    private final StringRedisTemplate redisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignTablePermissions(Long roleId, Long dataSourceId, List<String> tableNames) {
        this.remove(new LambdaQueryWrapper<TablePermission>()
                .eq(TablePermission::getRoleId, roleId)
                .eq(TablePermission::getDataSourceId, dataSourceId));

        if (!CollectionUtils.isEmpty(tableNames)) {
            List<TablePermission> permissions = tableNames.stream().map(name -> {
                TablePermission tp = new TablePermission();
                tp.setRoleId(roleId);
                tp.setDataSourceId(dataSourceId);
                tp.setTableName(name);
                tp.setPermission("SELECT");
                return tp;
            }).toList();
            this.saveBatch(permissions);
        }

        // 数据真正入库后再操作缓存，避免缓存读到未提交的变更
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    syncRedisCacheForRole(roleId);
                }
            });
        } else {
            syncRedisCacheForRole(roleId);
        }
    }

    private void syncRedisCacheForRole(Long roleId) {
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>().select(User::getId).eq(User::getRoleId, roleId)
        );
        if (CollectionUtils.isEmpty(users)) {
            return;
        }

        log.info("==> 权限变更事务提交，同步刷新角色 {} 关联的 {} 个用户缓存", roleId, users.size());
        users.forEach(user -> refreshUserPermissionsCache(user.getId(), roleId));
    }

    @Override
    public void refreshUserPermissionsCache(Long userId, Long roleId) {
        String permKey = SecurityConstants.USER_PERMISSION_KEY + userId;
        String policyKey = SecurityConstants.USER_POLICY_KEY + userId;

        // 加载最新数据
        List<TablePermission> perms = this.baseMapper.selectList(
                new LambdaQueryWrapper<TablePermission>().eq(TablePermission::getRoleId, roleId));
        QueryPolicy policy = queryPolicyMapper.selectOne(
                new LambdaQueryWrapper<QueryPolicy>().eq(QueryPolicy::getRoleId, roleId));

        // 刷新表权限：采用先删后增，确保无残留旧权限
        redisTemplate.delete(permKey);
        if (!CollectionUtils.isEmpty(perms)) {
            String[] permStrings = perms.stream()
                    .map(p -> p.getDataSourceId() + ":" + p.getTableName() + ":" + p.getPermission())
                    .toArray(String[]::new);
            redisTemplate.opsForSet().add(permKey, permStrings);
            // 24小时过期，增加1-60分钟随机偏移防止雪崩
            long expireMinutes = 1440 + (long) (Math.random() * 60);
            redisTemplate.expire(permKey, expireMinutes, TimeUnit.MINUTES);
        }

        // 刷新策略
        if (policy != null) {
            redisTemplate.opsForValue().set(policyKey, JSON.toJSONString(policy), 25, TimeUnit.HOURS);
        } else {
            redisTemplate.delete(policyKey);
        }
    }

    @Override
    public List<String> getAuthorizedTables(Long roleId, Long dataSourceId) {
        return this.list(new LambdaQueryWrapper<TablePermission>()
                        .eq(TablePermission::getRoleId, roleId)
                        .eq(TablePermission::getDataSourceId, dataSourceId))
                .stream()
                .map(TablePermission::getTableName)
                .toList();
    }

    @Override
    public boolean checkTableAccess(Long roleId, Long dataSourceId, String tableName) {
        Long count = this.baseMapper.selectCount(new LambdaQueryWrapper<TablePermission>()
                .eq(TablePermission::getRoleId, roleId)
                .eq(TablePermission::getDataSourceId, dataSourceId)
                .eq(TablePermission::getTableName, tableName));
        return count > 0;
    }
}