package com.xhx.core.service.management.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhx.common.exception.ServiceException;
import com.xhx.core.event.RolePermissionChangedEvent;
import com.xhx.core.service.cache.PermissionLoader;
import com.xhx.core.service.management.DataSourceService;
import com.xhx.core.service.management.RolePermissionService;
import com.xhx.dal.entity.*;
import com.xhx.dal.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 角色权限服务 - 最终完善版
 * @author master
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RolePermissionServiceImpl extends ServiceImpl<TablePermissionMapper, TablePermission> implements RolePermissionService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final DataSourceService dataSourceService;
    private final DataSourceMapper dataSourceMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final PermissionLoader permissionLoader;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignTablePermissions(Long roleId, Long dataSourceId, List<String> tableNames) {
        boolean roleExists = roleMapper.exists(new LambdaQueryWrapper<Role>().eq(Role::getId, roleId));
        if (!roleExists) {
            throw new ServiceException("角色不存在");
        }

        boolean ds = dataSourceMapper.exists(new LambdaQueryWrapper<DataSource>().eq(DataSource::getId, dataSourceId));
        if (!ds) {
            throw new ServiceException("数据源不存在");
        }

        // 校验是否是合法表名
        if (!CollectionUtils.isEmpty(tableNames)) {
            List<String> actualTables = dataSourceService.getTableNames(dataSourceId);

            Set<String> actualTableSet = new HashSet<>(actualTables);
            List<String> invalidTables = tableNames.stream()
                    .filter(name -> !actualTableSet.contains(name))
                    .toList();

            if (!invalidTables.isEmpty()) {
                throw new ServiceException("包含非法表名: " + invalidTables);
            }
        }

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

        // 事务提交后发布事件（异步失效缓存，不阻塞当前事务）
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
        // 兼容旧调用，委托给 PermissionLoader
        permissionLoader.doLoadFromDb(userId, roleId);
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

    @Override
    public Map<Long, List<String>> getRolePermissionSummary(Long roleId) {
        List<TablePermission> allPerms = this.list(
                new LambdaQueryWrapper<TablePermission>().eq(TablePermission::getRoleId, roleId)
        );

        if (allPerms.isEmpty()) {
            return Collections.emptyMap();
        }

        // 按数据源分组
        return allPerms.stream()
                .collect(Collectors.groupingBy(
                        TablePermission::getDataSourceId,
                        Collectors.mapping(TablePermission::getTableName, Collectors.toList())
                ));
    }
}