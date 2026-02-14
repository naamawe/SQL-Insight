package com.xhx.core.service.management;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xhx.dal.entity.Role;

/**
 * 角色管理服务
 * @author master
 */
public interface RoleService extends IService<Role> {

    /**
     * 创建角色
     */
    Long createRole(Role role);

    /**
     * 更新角色
     */
    void updateRole(Role role);

    /**
     * 删除角色（级联检查）
     */
    void deleteRole(Long id);
}