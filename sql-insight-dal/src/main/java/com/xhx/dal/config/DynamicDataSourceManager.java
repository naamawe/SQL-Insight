package com.xhx.dal.config;

import com.xhx.dal.entity.DataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author master
 */
@Component
@Slf4j
public class DynamicDataSourceManager {
    // 缓存数据源 ID -> 连接池
    private final Map<Long, HikariDataSource> poolCache = new ConcurrentHashMap<>();

    // 缓存数据源配置的 Hash 值，用于判断配置是否发生变化
    private final Map<Long, Integer> configHashCache = new ConcurrentHashMap<>();

    public javax.sql.DataSource getDataSource(DataSource dsConfig) {
        Long id = dsConfig.getId();
        int currentHash = Objects.hash(dsConfig.toJdbcUrl(), dsConfig.getUsername(), dsConfig.getPassword());

        // 如果配置变了，需要先关闭旧连接池
        if (configHashCache.containsKey(id) && !configHashCache.get(id).equals(currentHash)) {
            removeDataSource(id);
        }

        return poolCache.computeIfAbsent(id, key -> {
            log.info("==> 为数据源 {} [{}] 创建新的 Hikari 连接池", id, dsConfig.getConnName());
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(dsConfig.toJdbcUrl());
            config.setUsername(dsConfig.getUsername());
            config.setPassword(dsConfig.getPassword());
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(30000);
            config.setPoolName("SqlExecutor-Pool-" + id);

            configHashCache.put(id, currentHash);
            return new HikariDataSource(config);
        });
    }

    /**
     * 当数据源被删除或修改时，手动释放资源
     */
    public void removeDataSource(Long id) {
        HikariDataSource ds = poolCache.remove(id);
        configHashCache.remove(id);
        if (ds != null && !ds.isClosed()) {
            log.info("==> 正在关闭数据源 {} 的连接池", id);
            ds.close();
        }
    }
}
