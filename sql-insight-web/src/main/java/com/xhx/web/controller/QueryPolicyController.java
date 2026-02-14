package com.xhx.web.controller;

import com.xhx.common.result.Result;
import com.xhx.core.service.management.QueryPolicyService;
import com.xhx.dal.entity.QueryPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 查询策略管理控制层
 * @author master
 */
@RestController
@RequestMapping("/admin/query/policy")
@RequiredArgsConstructor
public class QueryPolicyController {

    private final QueryPolicyService queryPolicyService;

    /**
     * 根据角色 ID 获取当前策略
     */
    @GetMapping("/{roleId}")
    public Result<QueryPolicy> getPolicy(@PathVariable Long roleId) {
        QueryPolicy policy = queryPolicyService.getByRoleId(roleId);
        return Result.success(policy);
    }

    /**
     * 保存或更新策略
     */
    @PostMapping("/save")
    public Result<Void> savePolicy(@RequestBody QueryPolicy policy) {
        queryPolicyService.saveOrUpdatePolicy(policy);
        return Result.success();
    }
}