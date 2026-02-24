package com.xhx.core.event;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xhx.core.service.cache.CacheService;
import com.xhx.core.service.cache.PermissionLoader;
import com.xhx.core.service.sql.SchemaIndexingFacade;
import com.xhx.dal.entity.User;
import com.xhx.dal.mapper.UserDataSourceMapper;
import com.xhx.dal.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 缓存失效 + 向量索引联动事件监听器
 *
 * <p>职责：
 * <ul>
 *   <li>角色权限变更 → 失效该角色下所有用户的权限缓存</li>
 *   <li>数据源删除   → 清理 Redis 缓存 + 删除 Qdrant 向量索引</li>
 * </ul>
 *
 * <p>全部 {@code @Async}，不阻塞业务主事务。
 * 单个用户缓存失效失败时只记录日志，不影响其他操作。
 *
 * @author master
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheEvictEventListener {

    private final UserMapper                 userMapper;
    private final PermissionLoader           permissionLoader;
    private final CacheService               cacheService;
    private final UserDataSourceMapper       userDataSourceMapper;
    private final SchemaIndexingFacade       schemaIndexingFacade;

    // ==================== 角色权限变更 ====================

    @Async
    @EventListener
    public void onRolePermissionChanged(RolePermissionChangedEvent event) {
        Long roleId = event.getRoleId();
        log.info("[CacheEvict] 角色权限变更 roleId: {}，开始失效用户缓存", roleId);

        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .select(User::getId)
                        .eq(User::getRoleId, roleId));

        int success = 0;
        for (User user : users) {
            try {
                permissionLoader.evict(user.getId());
                success++;
            } catch (Exception e) {
                log.error("[CacheEvict] 用户 {} 权限缓存失效失败，TTL 到期后自动过期",
                        user.getId(), e);
            }
        }
        log.info("[CacheEvict] 角色 {} 失效 {}/{} 个用户缓存",
                roleId, success, users.size());
    }

    // ==================== 数据源删除 ====================

    @Async
    @EventListener
    public void onDataSourceDeleted(DataSourceDeletedEvent event) {
        Long dsId = event.getDataSourceId();
        log.info("[CacheEvict] 数据源删除 dsId: {}，清理缓存与向量索引", dsId);

        //失效 Redis 缓存
        cacheService.evictDsTables(dsId);
        cacheService.evictSchema(dsId);

        //失效关联用户权限缓存
        List<Long> userIds = userDataSourceMapper.selectUserIdsByDataSourceId(dsId);
        userIds.forEach(permissionLoader::evict);

        // 删除 Qdrant 向量索引
        schemaIndexingFacade.deleteIndex(dsId);

        log.info("[CacheEvict] 数据源 {} 清理完成：表名缓存、Schema 缓存、" +
                "{} 个用户缓存、向量索引", dsId, userIds.size());
    }

    /**
     * 监听新增/手动刷新事件
     */
    @Async("aiExecutor")
    @EventListener
    public void onDataSourceSync(DataSourceSyncEvent event) {
        Long dsId = event.getDataSourceId();
        log.info("[CacheEvict] 捕捉同步事件，触发向量索引重建: {}", dsId);
        schemaIndexingFacade.rebuildAsync(dsId);
    }
}