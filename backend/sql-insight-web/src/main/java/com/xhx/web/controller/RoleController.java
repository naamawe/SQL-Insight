package com.xhx.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhx.common.result.Result;
import com.xhx.core.model.dto.RoleSaveDTO;
import com.xhx.core.model.dto.RoleUpdateDTO;
import com.xhx.core.service.management.RoleService;
import com.xhx.dal.entity.Role;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
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
@PreAuthorize("hasRole('" + ADMIN + "')")
public class RoleController {

    private final RoleService roleService;

    /**
     * 获取所有角色列表
     */
    @GetMapping("/list")
    public Result<List<Role>> listAll() {
        return Result.success(roleService.list());
    }

    /**
     * 分页查询角色
     */
    @GetMapping("/page")
    public Result<Page<Role>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String roleName) {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(roleName)) {
            wrapper.like(Role::getRoleName, roleName);
        }
        wrapper.orderByAsc(Role::getId);
        return Result.success(roleService.page(new Page<>(current, size), wrapper));
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
    @PreAuthorize("hasRole('" + SUPER_ADMIN + "')")
    public Result<Long> addRole(@Valid @RequestBody RoleSaveDTO saveDTO) {
        Long roleId = roleService.createRole(saveDTO);
        return Result.success("角色创建成功", roleId);
    }

    /**
     * 修改角色信息
     */
    @PutMapping
    @PreAuthorize("hasRole('" + SUPER_ADMIN + "')")
    public Result<Void> updateRole(@Valid @RequestBody RoleUpdateDTO updateDTO) {
        roleService.updateRole(updateDTO);
        return Result.success("角色更新成功", null);
    }

    /**
     * 删除角色
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('" + SUPER_ADMIN + "')")
    public Result<Void> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return Result.success("角色删除成功", null);
    }
}