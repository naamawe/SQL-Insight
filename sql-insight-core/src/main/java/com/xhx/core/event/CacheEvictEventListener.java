package com.xhx.core.event;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xhx.core.service.cache.CacheService;
import com.xhx.core.service.cache.PermissionLoader;
import com.xhx.dal.entity.User;
import com.xhx.dal.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 缓存失效事件监听器
 * 使用 @Async 异步执行，不阻塞业务主流程
 * 即使 Redis 操作失败，也只是日志告警，不影响业务
 * @author master
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheEvictEventListener {

    private final UserMapper userMapper;
    private final PermissionLoader permissionLoader;
    private final CacheService cacheService;

    /**
     * 角色权限变更 → 失效该角色下所有用户的权限缓存
     */
    @Async
    @EventListener
    public void onRolePermissionChanged(RolePermissionChangedEvent event) {
        Long roleId = event.getRoleId();
        log.info("接收到角色权限变更事件，roleId: {}，开始失效用户缓存", roleId);

        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .select(User::getId)
                        .eq(User::getRoleId, roleId)
        );

        int successCount = 0;
        for (User user : users) {
            try {
                permissionLoader.evict(user.getId());
                successCount++;
            } catch (Exception e) {
                // 单个用户失效失败不影响其他用户，记录告警
                log.error("用户 {} 权限缓存失效失败，将在TTL到期后自动过期", user.getId(), e);
            }
        }

        log.info("角色 {} 权限变更，共失效 {}/{} 个用户缓存",
                roleId, successCount, users.size());
    }

    /**
     * 数据源删除 → 失效表名缓存
     */
    @Async
    @EventListener
    public void onDataSourceDeleted(DataSourceDeletedEvent event) {
        try {
            cacheService.evictDsTables(event.getDataSourceId());
            log.info("数据源 {} 表名缓存已失效", event.getDataSourceId());
        } catch (Exception e) {
            log.error("数据源 {} 表名缓存失效失败", event.getDataSourceId(), e);
        }
    }
}