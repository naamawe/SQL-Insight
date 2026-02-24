package com.xhx.web.controller;

import com.xhx.common.context.UserContext;
import com.xhx.common.result.Result;
import com.xhx.core.service.management.UserDataSourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.xhx.common.constant.SystemPermissionConstants.ADMIN;
import static com.xhx.common.constant.SystemPermissionConstants.USER;

/**
 * 用户数据源授权管理
 * @author master
 */
@RestController
@RequestMapping("/api/user-authorizations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('" + USER + "')")
public class UserDataSourceController {

    private final UserDataSourceService userDataSourceService;

    /**
     * 获取用户的数据源 ID 列表
     * * @param userId 用户 ID
     */
    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('" + ADMIN + "')")
    public Result<List<Long>> getAuthorizedIds(@PathVariable Long userId) {
        return Result.success(userDataSourceService.getAuthorizedDataSourceIds(userId));
    }

    /**
     * 获取自己的数据源 ID 列表
     * * @param userId 用户 ID
     */
    @GetMapping("/me")
    public Result<List<Long>> getMyAuthorizedIds() {
        Long userId = UserContext.getUserId();
        return Result.success(userDataSourceService.getAuthorizedDataSourceIds(userId));
    }

    /**
     * 为用户重新分配数据源权限（全量覆盖模式）
     * @param userId 用户 ID
     * @param dataSourceIds 新的数据源 ID 集合
     */
    @PostMapping("/{userId}")
    @PreAuthorize("hasRole('" + ADMIN + "')")
    public Result<Void> assign(@PathVariable Long userId, @RequestBody List<Long> dataSourceIds) {
        userDataSourceService.assignDataSources(userId, dataSourceIds);
        return Result.success();
    }
}