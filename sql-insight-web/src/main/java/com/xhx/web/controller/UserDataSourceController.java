package com.xhx.web.controller;

import com.xhx.common.result.Result;
import com.xhx.core.service.UserDataSourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户数据源授权管理
 * @author master
 */
@RestController
@RequestMapping("/api/user-authorizations")
@RequiredArgsConstructor
public class UserDataSourceController {

    private final UserDataSourceService userDataSourceService;

    /**
     * 获取用户已授权的数据源 ID 列表
     * * @param userId 用户 ID
     */
    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public Result<List<Long>> getAuthorizedIds(@PathVariable Long userId) {
        return Result.success(userDataSourceService.getAuthorizedDataSourceIds(userId));
    }

    /**
     * 为用户重新分配数据源权限（全量覆盖模式）
     * @param userId 用户 ID
     * @param dataSourceIds 新的数据源 ID 集合
     */
    @PostMapping("/{userId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public Result<Void> assign(@PathVariable Long userId, @RequestBody List<Long> dataSourceIds) {
        userDataSourceService.assignDataSources(userId, dataSourceIds);
        return Result.success();
    }
}