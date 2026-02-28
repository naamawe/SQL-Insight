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
        // 参数校验
        if (policy.getRoleId() == null) {
            return Result.error(400, "角色ID不能为空");
        }
        if (policy.getMaxLimit() == null || policy.getMaxLimit() < 1 || policy.getMaxLimit() > 10000) {
            return Result.error(400, "最大行数限制必须在1-10000之间");
        }
        if (policy.getAllowJoin() == null || (policy.getAllowJoin() != 0 && policy.getAllowJoin() != 1)) {
            return Result.error(400, "allowJoin必须为0或1");
        }
        if (policy.getAllowSubquery() == null || (policy.getAllowSubquery() != 0 && policy.getAllowSubquery() != 1)) {
            return Result.error(400, "allowSubquery必须为0或1");
        }
        if (policy.getAllowAggregation() == null || (policy.getAllowAggregation() != 0 && policy.getAllowAggregation() != 1)) {
            return Result.error(400, "allowAggregation必须为0或1");
        }
        
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