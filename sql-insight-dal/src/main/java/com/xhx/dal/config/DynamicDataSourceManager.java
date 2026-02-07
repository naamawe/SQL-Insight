package com.xhx.dal.config;

import com.xhx.dal.entity.DataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author master
 */
@Component
public class DynamicDataSourceManager {
    private final Map<Long, HikariDataSource> poolCache = new ConcurrentHashMap<>();

    public javax.sql.DataSource getDataSource(DataSource dsConfig) {
        return poolCache.computeIfAbsent(dsConfig.getId(), id -> {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(dsConfig.toJdbcUrl());
            config.setUsername(dsConfig.getUsername());
            config.setPassword(dsConfig.getPassword());
            config.setMaximumPoolSize(5);
            config.setMinimumIdle(1);
            config.setPoolName("TenantPool-" + id);
            return new HikariDataSource(config);
        });
    }
}