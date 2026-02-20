package com.xhx.core.cache;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xhx.core.service.cache.PermissionLoader;
import com.google.common.collect.Lists;
import com.xhx.dal.entity.User;
import com.xhx.dal.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 权限缓存预热
 * 系统启动后分批加载所有活跃用户的权限到 Redis
 * 避免冷启动时大量请求打穿到 DB
 * @author master
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionCacheWarmUp implements ApplicationRunner {

    private final UserMapper userMapper;
    private final PermissionLoader permissionLoader;

    private static final int BATCH_SIZE = 50;
    private static final long BATCH_SLEEP_MS = 50;

    @Override
    public void run(ApplicationArguments args) {
        log.info("开始预热权限缓存...");

        List<User> activeUsers = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .select(User::getId, User::getRoleId)
                        .eq(User::getStatus, 1)
        );

        if (activeUsers.isEmpty()) {
            log.info("无活跃用户，跳过预热");
            return;
        }

        // 分批处理，避免瞬间打爆 DB
        List<List<User>> batches = Lists.partition(activeUsers, BATCH_SIZE);
        int success = 0;

        for (List<User> batch : batches) {
            for (User user : batch) {
                try {
                    permissionLoader.doLoadFromDb(user.getId(), user.getRoleId());
                    success++;
                } catch (Exception e) {
                    log.warn("用户 {} 权限预热失败，跳过（首次请求时懒加载）",
                            user.getId(), e);
                }
            }
            // 批次间停顿，保护 DB
            if (BATCH_SLEEP_MS > 0) {
                try {
                    Thread.sleep(BATCH_SLEEP_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.info("权限缓存预热完成，成功: {}/{}",
                success, activeUsers.size());
    }
}