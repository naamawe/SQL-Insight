package com.xhx.web.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhx.common.result.Result;
import com.xhx.core.model.dto.RoleSaveDTO;
import com.xhx.core.model.dto.RoleUpdateDTO;
import com.xhx.core.model.vo.RoleVO;
import com.xhx.core.service.management.RoleService;
import jakarta.validation.Valid;
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
@PreAuthorize("hasRole('" + ADMIN + "')")
public class RoleController {

    private final RoleService roleService;

    /**
     * 分页查询角色列表
     */
    @GetMapping("/page")
    public Result<Page<RoleVO>> getRolePage(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String roleName) {
        // 限制单次最多查询100条
        if (size > 100) {
            size = 100;
        }
        return Result.success(roleService.getRolePage(current, size, roleName));
    }

    /**
     * 获取所有角色列表（不分页，用于下拉框）
     */
    @GetMapping("/list")
    public Result<List<RoleVO>> listAll() {
        return Result.success(roleService.listAllRoles());
    }

    /**
     * 根据 ID 获取角色详情
     */
    @GetMapping("/{id}")
    public Result<RoleVO> getById(@PathVariable Long id) {
        return Result.success(roleService.getRoleById(id));
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