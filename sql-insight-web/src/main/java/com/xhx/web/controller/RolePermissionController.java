package com.xhx.web.controller;

import com.xhx.common.result.Result;
import com.xhx.core.service.RolePermissionService;
import com.xhx.web.dto.PermissionAssignDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色权限管理控制层
 * @author master
 */
@RestController
@RequestMapping("/admin/role/permission")
@RequiredArgsConstructor
public class RolePermissionController {

    private final RolePermissionService rolePermissionService;

    /**
     * 获取角色已授权的表列表
     */
    @GetMapping("/tables")
    public Result<List<String>> getAuthorizedTables(@RequestParam Long roleId, 
                                                    @RequestParam Long dataSourceId) {
        List<String> tables = rolePermissionService.getAuthorizedTables(roleId, dataSourceId);
        return Result.success(tables);
    }

    /**
     * 保存角色权限映射
     */
    @PostMapping("/assign")
    public Result<Void> assignPermissions(@RequestBody PermissionAssignDTO dto) {
        rolePermissionService.assignTablePermissions(
                dto.getRoleId(), 
                dto.getDataSourceId(), 
                dto.getTableNames()
        );
        return Result.success();
    }
}