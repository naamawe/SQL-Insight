package com.xhx.core.service.management.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhx.core.event.RolePermissionChangedEvent;
import com.xhx.core.service.management.QueryPolicyService;
import com.xhx.dal.entity.QueryPolicy;
import com.xhx.dal.mapper.QueryPolicyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;


/**
 * @author master
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryPolicyServiceImpl extends ServiceImpl<QueryPolicyMapper, QueryPolicy> implements QueryPolicyService {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveOrUpdatePolicy(QueryPolicy policy) {
        this.saveOrUpdate(policy);

        // 事务提交后发布事件
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        eventPublisher.publishEvent(
                                new RolePermissionChangedEvent(this, policy.getRoleId()));
                    }
                }
        );
    }

    @Override
    public QueryPolicy getByRoleId(Long roleId) {
        return this.getOne(new LambdaQueryWrapper<QueryPolicy>().eq(QueryPolicy::getRoleId, roleId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePolicy(Long roleId) {
        this.remove(new LambdaQueryWrapper<QueryPolicy>()
                .eq(QueryPolicy::getRoleId, roleId));

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        eventPublisher.publishEvent(
                                new RolePermissionChangedEvent(this, roleId));
                    }
                }
        );
    }
}