package com.xhx.core.service.management.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRole(Role role) {
        // 防止同名角色
        long count = this.count(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleName, role.getRoleName()));
        if (count > 0) {
            throw new RuntimeException("角色名称 [" + role.getRoleName() + "] 已存在");
        }

        this.save(role);
        log.info("==> 角色 [{}] 创建成功，ID: {}", role.getRoleName(), role.getId());
        return role.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(Role role) {
        if (role.getId() == null) {
            throw new RuntimeException("角色ID不能为空");
        }

        Role existingRole = this.getById(role.getId());
        if (existingRole == null) {
            throw new RuntimeException("角色不存在");
        }

        // 检查新名称是否与其他角色重复
        long count = this.count(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleName, role.getRoleName())
                .ne(Role::getId, role.getId()));

        if (count > 0) {
            throw new RuntimeException("角色名称 [" + role.getRoleName() + "] 已存在");
        }

        this.updateById(role);
        log.info("==> 角色 [{}] 更新成功", role.getRoleName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        Role role = this.getById(id);
        if (role == null) {
            throw new RuntimeException("角色不存在");
        }

        // 检查是否有用户正在使用该角色
        Long userCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getRoleId, id)
        );

        if (userCount > 0) {
            throw new RuntimeException("该角色下还有 " + userCount + " 个用户，无法删除");
        }

        // 级联删除权限配置
        Long permCount = tablePermissionMapper.selectCount(
                new LambdaQueryWrapper<TablePermission>().eq(TablePermission::getRoleId, id)
        );

        if (permCount > 0) {
            tablePermissionMapper.delete(
                    new LambdaQueryWrapper<TablePermission>().eq(TablePermission::getRoleId, id)
            );
            log.info("==> 删除角色时，已级联删除 {} 条表权限配置", permCount);
        }

        // 级联删除查询策略
        queryPolicyMapper.delete(
                new LambdaQueryWrapper<QueryPolicy>().eq(QueryPolicy::getRoleId, id)
        );

        // 删除角色
        this.removeById(id);
        log.info("==> 角色 [{}] 删除成功", role.getRoleName());
    }
}