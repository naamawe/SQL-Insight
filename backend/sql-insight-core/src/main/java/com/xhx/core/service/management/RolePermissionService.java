package com.xhx.core.service.management;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xhx.dal.entity.TablePermission;
import java.util.List;
import java.util.Map;

/**
 * 角色表权限服务接口
 * @author master
 */
public interface RolePermissionService extends IService<TablePermission> {

    /**
     * 为某个角色分配在特定数据源下的表权限
     * 内部包含事务成功后的缓存刷新逻辑
     */
    void assignTablePermissions(Long roleId, Long dataSourceId, List<String> tableNames);

    /**
     * 获取某个角色在特定数据源下已授权的表名列表
     */
    List<String> getAuthorizedTables(Long roleId, Long dataSourceId);

    /**
     * 校验该角色是否有权操作某张表
     */
    boolean checkTableAccess(Long roleId, Long dataSourceId, String tableName);

    /**
     * 主动刷新特定用户的权限快照到 Redis
     * @param userId 用户ID
     * @param roleId 角色ID
     */
    void refreshUserPermissionsCache(Long userId, Long roleId);

    /**
     * 获取角色在所有数据源下的权限汇总
     * @param roleId 角色ID
     * @return Map<数据源ID, 表名列表>
     */
    Map<Long, List<String>> getRolePermissionSummary(Long roleId);
}