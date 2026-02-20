package com.xhx.core.service.management.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhx.common.exception.ServiceException;
import com.xhx.core.event.RolePermissionChangedEvent;
import com.xhx.core.model.dto.RoleSaveDTO;
import com.xhx.core.model.dto.RoleUpdateDTO;
import com.xhx.core.service.management.RoleService;
import com.xhx.dal.entity.QueryPolicy;
import com.xhx.dal.entity.Role;
import com.xhx.dal.entity.TablePermission;
import com.xhx.dal.entity.User;
import com.xhx.dal.mapper.QueryPolicyMapper;
import com.xhx.dal.mapper.RoleMapper;
import com.xhx.dal.mapper.TablePermissionMapper;
import com.xhx.dal.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
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
public class RoleServiceImpl extends ServiceImpl<RoleMapper, Role> implements RoleService {

    private final UserMapper userMapper;
    private final TablePermissionMapper tablePermissionMapper;
    private final QueryPolicyMapper queryPolicyMapper;
    private final ApplicationEventPublisher eventPublisher;
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRole(RoleSaveDTO saveDTO) {
        long count = this.count(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleName, saveDTO.getRoleName()));
        if (count > 0) {
            throw new ServiceException("角色名称 [" + saveDTO.getRoleName() + "] 已存在");
        }

        Role role = new Role();
        BeanUtils.copyProperties(saveDTO, role);
        this.save(role);

        log.info("==> 角色 [{}] 创建成功，ID: {}", role.getRoleName(), role.getId());
        return role.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(RoleUpdateDTO updateDTO) {
        Role existingRole = this.getById(updateDTO.getId());
        if (existingRole == null) {
            throw new ServiceException("角色不存在");
        }

        long count = this.count(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleName, updateDTO.getRoleName())
                .ne(Role::getId, updateDTO.getId()));
        if (count > 0) {
            throw new ServiceException("角色名称 [" + updateDTO.getRoleName() + "] 已存在");
        }

        BeanUtils.copyProperties(updateDTO, existingRole);
        this.updateById(existingRole);
        log.info("==> 角色 [{}] 更新成功", existingRole.getRoleName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        Role role = this.getById(id);
        if (role == null) {
            throw new ServiceException("角色不存在");
        }

        Long userCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getRoleId, id)
        );
        if (userCount > 0) {
            throw new ServiceException("该角色下还有 " + userCount + " 个用户，无法删除");
        }

        Long permCount = tablePermissionMapper.selectCount(
                new LambdaQueryWrapper<TablePermission>().eq(TablePermission::getRoleId, id)
        );
        if (permCount > 0) {
            tablePermissionMapper.delete(
                    new LambdaQueryWrapper<TablePermission>().eq(TablePermission::getRoleId, id)
            );
            log.info("==> 删除角色时，已级联删除 {} 条表权限配置", permCount);
        }

        queryPolicyMapper.delete(
                new LambdaQueryWrapper<QueryPolicy>().eq(QueryPolicy::getRoleId, id)
        );

        this.removeById(id);

        // 在 removeById(id) 之后
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        eventPublisher.publishEvent(new RolePermissionChangedEvent(this, id));
                    }
                }
        );
        log.info("==> 角色 [{}] 删除成功", role.getRoleName());
    }
}