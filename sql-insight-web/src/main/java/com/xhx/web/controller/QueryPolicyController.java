package com.xhx.web.controller;

import com.xhx.common.result.Result;
import com.xhx.core.service.management.QueryPolicyService;
import com.xhx.dal.entity.QueryPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static com.xhx.common.constant.SystemPermissionConstants.ADMIN;

/**
 * 查询策略管理
 * @author master
 */
@RestController
@RequestMapping("/api/query-policies")
@RequiredArgsConstructor
@PreAuthorize("hasRole('" + ADMIN + "')")
public class QueryPolicyController {

    private final QueryPolicyService queryPolicyService;

    /**
     * 根据角色ID获取查询策略
     */
    @GetMapping
    public Result<QueryPolicy> getPolicy(@RequestParam Long roleId) {
        QueryPolicy policy = queryPolicyService.getByRoleId(roleId);
        return Result.success(policy);
    }

    /**
     * 保存或更新查询策略
     */
    @PostMapping
    public Result<Void> savePolicy(@RequestBody QueryPolicy policy) {
        queryPolicyService.saveOrUpdatePolicy(policy);
        return Result.success("策略保存成功", null);
    }

    /**
     * 删除查询策略
     */
    @DeleteMapping
    public Result<Void> deletePolicy(@RequestParam Long roleId) {
        queryPolicyService.deletePolicy(roleId);
        return Result.success("策略已删除", null);
    }
}