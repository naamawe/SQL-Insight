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
 * @author master
 */
@RestController
@RequestMapping("/admin/role")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + ADMIN + "')")
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
     */
    @GetMapping("/list")
    public Result<List<Role>> listAll() {
        return Result.success(roleService.list());
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