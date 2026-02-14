package com.xhx.core.service.management.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhx.common.constant.SystemPermissionConstants;
import com.xhx.common.context.UserContext;
import com.xhx.core.service.management.RoleService;
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
}