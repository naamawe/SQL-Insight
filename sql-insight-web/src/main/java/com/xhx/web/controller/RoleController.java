package com.xhx.web.controller;

import com.xhx.common.result.Result;
import com.xhx.core.service.RoleService;
import com.xhx.dal.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.xhx.common.constant.SystemPermissionConstants.ADMIN;
import static com.xhx.common.constant.SystemPermissionConstants.SUPER_ADMIN;

/**
 * @author master
 */
@RestController
@RequestMapping("/admin/role")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    /**
     * 创建新角色（挂载点）
     * 只有超级管理员可以定义新的角色
     */
    @PreAuthorize("hasAuthority('" + SUPER_ADMIN + "')")
    @PostMapping("/add")
    public Result<Long> addRole(@RequestBody Role role) {
        return Result.success(roleService.createRole(role));
    }

    /**
     * 获取所有角色列表
     * 管理员及以上可见，用于在给用户分配角色时进行下拉选择
     */
    @PreAuthorize("hasAnyAuthority('" + SUPER_ADMIN + "', '" + ADMIN + "')")
    @GetMapping("/list")
    public Result<List<Role>> listAll() {
        return Result.success(roleService.list());
    }

    /**
     * 根据系统权限类型获取角色
     */
    @GetMapping("/by-permission")
    public Result<List<Role>> getByPermission(@RequestParam String permission) {
        return Result.success(roleService.getRolesBySystemPermission(permission));
    }

    /**
     * 删除角色
     */
    @PreAuthorize("hasAuthority('" + SUPER_ADMIN + "')")
    @DeleteMapping("/{id}")
    public Result<Void> deleteRole(@PathVariable Long id) {
        roleService.removeById(id);
        return Result.success();
    }
}