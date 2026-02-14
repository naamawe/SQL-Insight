package com.xhx.core.service.management;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xhx.dal.entity.Role;

/**
 * 角色职能管理服务
 * @author master
 */
public interface RoleService extends IService<Role> {

    /**
     * 创建职能角色
     */
    Long createRole(Role role);
}