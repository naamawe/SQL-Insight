package com.xhx.core.service.management.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhx.core.service.management.QueryPolicyService;
import com.xhx.core.service.management.RolePermissionService;
import com.xhx.dal.entity.QueryPolicy;
import com.xhx.dal.entity.User;
import com.xhx.dal.mapper.QueryPolicyMapper;
import com.xhx.dal.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * @author master
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryPolicyServiceImpl extends ServiceImpl<QueryPolicyMapper, QueryPolicy> implements QueryPolicyService {

    private final UserMapper userMapper;
    private final RolePermissionService rolePermissionService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveOrUpdatePolicy(QueryPolicy policy) {
        // 数据库更新
        this.saveOrUpdate(policy);

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    refreshUsersUnderRole(policy.getRoleId());
                }
            });
        } else {
            refreshUsersUnderRole(policy.getRoleId());
        }
    }

    private void refreshUsersUnderRole(Long roleId) {
        // 找到该角色下所有用户
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>().select(User::getId).eq(User::getRoleId, roleId));
        
        // 调用 RolePermissionService 中那个“全能”的刷新方法
        // 因为那个方法同时负责刷新表权限和 Policy 策略到 Redis
        users.forEach(user -> rolePermissionService.refreshUserPermissionsCache(user.getId(), roleId));
        
        log.info("==> 查询策略已变更，已同步刷新角色 {} 下 {} 个用户的安全快照", roleId, users.size());
    }

    @Override
    public QueryPolicy getByRoleId(Long roleId) {
        return this.getOne(new LambdaQueryWrapper<QueryPolicy>().eq(QueryPolicy::getRoleId, roleId));
    }
}