package com.xhx.web.controller;

import com.xhx.common.result.Result;
import com.xhx.core.model.dto.PermissionAssignDTO;
import com.xhx.core.service.management.RolePermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static com.xhx.common.constant.SystemPermissionConstants.ADMIN;

/**
 * 角色表权限管理
 * @author master
 */
@RestController
@RequestMapping("/api/role-permissions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('" + ADMIN + "')")
public class RolePermissionController {

    private final RolePermissionService rolePermissionService;

    /**
     * 获取角色在特定数据源下的已授权表列表
     */
    @GetMapping("/tables")
    public Result<List<String>> getAuthorizedTables(
            @RequestParam Long roleId,
            @RequestParam Long dataSourceId) {
        List<String> tables = rolePermissionService.getAuthorizedTables(roleId, dataSourceId);
        return Result.success(tables);
    }

    /**
     * 获取角色在所有数据源下的权限汇总
     */
    @GetMapping("/summary")
    public Result<Map<Long, List<String>>> getRolePermissionSummary(@RequestParam Long roleId) {
        Map<Long, List<String>> summary = rolePermissionService.getRolePermissionSummary(roleId);
        return Result.success(summary);
    }

    /**
     * 为角色分配表权限
     */
    @PostMapping
    public Result<Void> assignPermissions(@RequestBody PermissionAssignDTO dto) {
        // 参数校验
        if (dto.getRoleId() == null) {
            return Result.error(400, "角色ID不能为空");
        }
        if (dto.getDataSourceId() == null) {
            return Result.error(400, "数据源ID不能为空");
        }
        if (dto.getTableNames() != null && dto.getTableNames().size() > 500) {
            return Result.error(400, "单次最多授权500张表");
        }
        
        rolePermissionService.assignTablePermissions(
                dto.getRoleId(),
                dto.getDataSourceId(),
                dto.getTableNames()
        );
        return Result.success("权限分配成功", null);
    }

    /**
     * 清空角色在特定数据源下的所有权限
     */
    @DeleteMapping
    public Result<Void> clearPermissions(
            @RequestParam Long roleId,
            @RequestParam Long dataSourceId) {
        rolePermissionService.assignTablePermissions(roleId, dataSourceId, null);
        return Result.success("权限已清空", null);
    }
}