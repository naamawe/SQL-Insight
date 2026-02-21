package com.xhx.dal.config;

import com.xhx.dal.entity.DataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author master
 */
@Component
@Slf4j
public class DynamicDataSourceManager {

    private final Map<Long, HikariDataSource> poolCache = new ConcurrentHashMap<>();
    private final Map<Long, Integer> configHashCache = new ConcurrentHashMap<>();
    private final Map<Long, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    public javax.sql.DataSource getDataSource(DataSource dsConfig) {
        Long id = dsConfig.getId();
        int currentHash = Objects.hash(
                dsConfig.toJdbcUrl(), dsConfig.getUsername(), dsConfig.getPassword());

        HikariDataSource existing = poolCache.get(id);
        if (existing != null && Objects.equals(configHashCache.get(id), currentHash)) {
            return existing;
        }

        ReentrantLock lock = lockMap.computeIfAbsent(id, k -> new ReentrantLock());
        lock.lock();
        try {
            existing = poolCache.get(id);
            if (existing != null && Objects.equals(configHashCache.get(id), currentHash)) {
                return existing;
            }

            // 配置变更：先销毁旧连接池
            if (existing != null) {
                log.info("==> 数据源 {} 配置已变更，销毁旧连接池", id);
                removeDataSourceInternal(id, existing);
            }

            // 创建新连接池
            log.info("==> 为数据源 {} [{}] 创建新的 Hikari 连接池", id, dsConfig.getConnName());
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(dsConfig.toJdbcUrl());
            config.setUsername(dsConfig.getUsername());
            config.setPassword(dsConfig.getPassword());
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(30000);
            config.setPoolName("SqlExecutor-Pool-" + id);

            HikariDataSource newDs = new HikariDataSource(config);
            poolCache.put(id, newDs);
            configHashCache.put(id, currentHash);
            return newDs;

        } finally {
            lock.unlock();
        }
    }

    public void removeDataSource(Long id) {
        ReentrantLock lock = lockMap.computeIfAbsent(id, k -> new ReentrantLock());
        lock.lock();
        try {
            HikariDataSource ds = poolCache.get(id);
            if (ds != null) {
                removeDataSourceInternal(id, ds);
            }
        } finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("resource")
    private void removeDataSourceInternal(Long id, HikariDataSource ds) {
        poolCache.remove(id);
        configHashCache.remove(id);
        if (!ds.isClosed()) {
            log.info("==> 正在关闭数据源 {} 的连接池", id);
            ds.close();
        }
    }
}