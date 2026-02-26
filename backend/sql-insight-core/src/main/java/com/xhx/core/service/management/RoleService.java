package com.xhx.core.service.management;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xhx.core.model.dto.RoleSaveDTO;
import com.xhx.core.model.dto.RoleUpdateDTO;
import com.xhx.core.model.vo.RoleVO;
import com.xhx.dal.entity.Role;

import java.util.List;

/**
 * 角色管理服务
 * @author master
 */
public interface RoleService extends IService<Role> {

    /**
     * 分页查询角色列表
     * @param current  当前页
     * @param size     每页大小
     * @param roleName 角色名（模糊，可为空）
     */
    Page<RoleVO> getRolePage(int current, int size, String roleName);

    /**
     * 获取所有角色列表（不分页，用于下拉框）
     */
    List<RoleVO> listAllRoles();

    /**
     * 根据 ID 获取角色详情
     */
    RoleVO getRoleById(Long id);

    /**
     * 创建角色
     */
    Long createRole(RoleSaveDTO saveDTO);

    /**
     * 更新角色
     */
    void updateRole(RoleUpdateDTO updateDTO);

    /**
     * 删除角色（级联检查）
     */
    void deleteRole(Long id);
}