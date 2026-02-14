package com.xhx.core.service.management;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xhx.dal.entity.Role;
import java.util.List;

/**
 * 角色职能管理服务
 * @author master
 */
public interface RoleService extends IService<Role> {

    /**
     * 创建职能角色
     */
    Long createRole(Role role);

    /**
     * 获取或自动创建一个符合系统权限要求的默认角色
     * 用于解决用户注册时 role_id 不能为空的硬性约束
     */
    Role getOrCreateDefaultRole(String systemPermission);

    /**
     * 获取所有具备特定系统权限的角色
     */
    List<Role> getRolesBySystemPermission(String systemPermission);
}