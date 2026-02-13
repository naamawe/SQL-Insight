package com.xhx.core.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhx.common.constant.SystemPermissionConstants;
import com.xhx.core.service.RoleService;
import com.xhx.dal.entity.Role;
import com.xhx.dal.mapper.RoleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author master
 */
@Slf4j
@Service
public class RoleServiceImpl extends ServiceImpl<RoleMapper, Role> implements RoleService {

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
        return role.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Role getOrCreateDefaultRole(String systemPermission) {
        // 查找该权限级别下的第一个可用角色
        Role role = this.getOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getSystemPermission, systemPermission)
                .last("LIMIT 1"));

        // 如果不存在（通常是系统首次运行），则自动创建一个种子角色
        if (role == null) {
            log.info("==> 库中无 [{}] 类型角色，正在自动创建种子角色...", systemPermission);
            role = new Role();
            String defaultName = switch (systemPermission) {
                case SystemPermissionConstants.SUPER_ADMIN -> "初始化超级管理员";
                case SystemPermissionConstants.ADMIN -> "初始化管理员";
                default -> "初始分析师角色";
            };
            role.setRoleName(defaultName);
            role.setSystemPermission(systemPermission);
            role.setDescription("系统自动生成的预置角色");
            this.save(role);
        }
        return role;
    }

    @Override
    public List<Role> getRolesBySystemPermission(String systemPermission) {
        return this.list(new LambdaQueryWrapper<Role>()
                .eq(Role::getSystemPermission, systemPermission));
    }
}