package com.xhx.core.service.management;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xhx.dal.entity.QueryPolicy;

/**
 * 查询策略服务接口
 * @author master
 */
public interface QueryPolicyService extends IService<QueryPolicy> {

    /**
     * 保存或更新角色的查询策略
     * 变更后需同步刷新该角色下所有用户的缓存
     */
    void saveOrUpdatePolicy(QueryPolicy policy);

    /**
     * 根据角色 ID 获取策略
     */
    QueryPolicy getByRoleId(Long roleId);

    /**
     * 删除查询策略
     */
    void deletePolicy(Long roleId);
}