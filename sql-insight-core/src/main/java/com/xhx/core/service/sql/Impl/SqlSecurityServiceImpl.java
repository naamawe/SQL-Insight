package com.xhx.core.service.sql.Impl;

import com.alibaba.fastjson2.JSON;
import com.xhx.core.service.cache.CacheService;
import com.xhx.core.service.sql.SqlSecurityService;
import com.xhx.dal.entity.QueryPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * SQL 安全校验服务
 * @author master
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlSecurityServiceImpl implements SqlSecurityService {

    private final CacheService cacheService;
    @Override
    public void validate(String sql, Long userId, Long dataSourceId) {
        log.info("[安全审计] userId: {}, dsId: {}, sql: {}", userId, dataSourceId, sql);
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            TablesNamesFinder finder = new TablesNamesFinder();
            List<String> tableNames = finder.getTableList(statement);

            checkTableAccess(userId, dataSourceId, tableNames);
            checkPolicy(userId, sql);
        } catch (Exception e) {
            log.error("[审计未通过] {}", e.getMessage());
            throw new RuntimeException("SQL 安全风险拦截: " + e.getMessage());
        }
    }

    /**
     * 对比 Redis 中的表权限快照
     * Redis 中存储格式为：{dataSourceId}:{tableName}:{PERMISSION}，例如 "1:orders:SELECT"
     */
    private void checkTableAccess(Long userId, Long dataSourceId, List<String> tableNames) {
        // 此处直接读缓存（generate 时已经懒加载过，这里必然命中）
        Set<String> allPerms = cacheService.getUserPermissions(userId);
        if (allPerms == null) {
            throw new RuntimeException("权限信息不可用，请重新登录后再试");
        }
        for (String tableName : tableNames) {
            String required = dataSourceId + ":" + tableName + ":SELECT";
            if (!allPerms.contains(required)) {
                throw new RuntimeException("无权访问表: " + tableName);
            }
        }
    }

    /**
     * 对比 Redis 中的策略 JSON
     */
    private void checkPolicy(Long userId, String sql) {
        String policyJson = cacheService.getUserPolicy(userId);
        if (policyJson == null) {
            return;
        }

        QueryPolicy policy = JSON.parseObject(policyJson, QueryPolicy.class);
        String upper = sql.toUpperCase();

        if (policy.getAllowJoin() == 0 &&
                (upper.contains("JOIN") || upper.contains(","))) {
            throw new RuntimeException("当前策略禁止关联查询(JOIN)");
        }
        if (policy.getAllowSubquery() == 0 && isSubquery(upper)) {
            throw new RuntimeException("当前策略禁止执行子查询");
        }
        if (policy.getAllowAggregation() == 0 && isAggregation(upper)) {
            throw new RuntimeException("当前策略禁止执行聚合统计");
        }
        if (!upper.contains("LIMIT")) {
            throw new RuntimeException("必须包含 LIMIT，最大允许 " + policy.getMaxLimit() + " 行");
        }
    }

    private boolean isSubquery(String sql) {
        return sql.indexOf("SELECT", 1) > 0;
    }

    private boolean isAggregation(String sql) {
        return sql.contains("COUNT(") || sql.contains("SUM(") || sql.contains("AVG(") || sql.contains("MAX(");
    }
}