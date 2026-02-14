package com.xhx.web.controller;

import com.xhx.common.result.Result;
import com.xhx.core.service.management.RoleService;
import com.xhx.dal.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.xhx.common.constant.SystemPermissionConstants.ADMIN;
import static com.xhx.common.constant.SystemPermissionConstants.SUPER_ADMIN;

/**
 * 角色管理
 * @author master
 */
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + ADMIN + "')")
public class RoleController {
    // TODO 需要把role实体类改为dto包装类

    private final RoleService roleService;

    /**
     * 获取所有角色列表
     */
    @GetMapping("/list")
    public Result<List<Role>> listAll() {
        return Result.success(roleService.list());
    }

    /**
     * 根据ID获取角色详情
     */
    @GetMapping("/{id}")
    public Result<Role> getById(@PathVariable Long id) {
        Role role = roleService.getById(id);
        if (role == null) {
            return Result.error("角色不存在");
        }
        return Result.success(role);
    }

    /**
     * 创建新角色
     */
    @PostMapping
    @PreAuthorize("hasAuthority('" + SUPER_ADMIN + "')")
    public Result<Long> addRole(@RequestBody Role role) {
        Long roleId = roleService.createRole(role);
        return Result.success("角色创建成功", roleId);
    }

    /**
     * 修改角色信息
     */
    @PutMapping
    @PreAuthorize("hasAuthority('" + SUPER_ADMIN + "')")
    public Result<Void> updateRole(@RequestBody Role role) {
        roleService.updateRole(role);
        return Result.success("角色更新成功", null);
    }

    /**
     * 删除角色
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + SUPER_ADMIN + "')")
    public Result<Void> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return Result.success("角色删除成功", null);
    }
}