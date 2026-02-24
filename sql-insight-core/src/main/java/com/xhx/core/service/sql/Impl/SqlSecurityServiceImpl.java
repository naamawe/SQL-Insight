package com.xhx.core.service.sql.Impl;

import com.alibaba.fastjson2.JSON;
import com.xhx.common.util.CommonUtil;
import com.xhx.core.service.cache.CacheService;
import com.xhx.core.service.sql.SqlSecurityService;
import com.xhx.dal.entity.DataSource;
import com.xhx.dal.entity.QueryPolicy;
import com.xhx.dal.mapper.DataSourceMapper;
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
    private final DataSourceMapper dataSourceMapper;

    @Override
    public void validate(String sql, Long userId, Long dataSourceId) {
        log.info("[安全审计] userId: {}, dsId: {}, sql: {}", userId, dataSourceId, sql);

        if (CommonUtil.isExplain(sql)) {
            log.info("[安全审计] 无权限表，跳过 SQL 审计");
            return;
        }

        try {
            Statement statement = CCJSqlParserUtil.parse(sql);

            TablesNamesFinder finder = new TablesNamesFinder();
            Set<String> tableSet = finder.getTables(statement);

            List<String> tableNames = tableSet.stream()
                    .map(String::toLowerCase)
                    .toList();

            checkTableAccess(userId, dataSourceId, tableNames);

            // 查出 dbType，用于行数限制的方言适配
            DataSource dsConfig = dataSourceMapper.selectById(dataSourceId);
            String dbType = dsConfig != null ? dsConfig.getDbType().toLowerCase() : "mysql";

            checkPolicy(userId, sql, dbType);

        } catch (Exception e) {
            log.error("[审计未通过] {}", e.getMessage());
            throw new RuntimeException("SQL 安全风险拦截: " + e.getMessage());
        }
    }

    /**
     * 对比 Redis 中的表权限快照
     */
    private void checkTableAccess(Long userId, Long dataSourceId, List<String> tableNames) {
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
     * 行数限制检查按数据库方言区分：
     */
    private void checkPolicy(Long userId, String sql, String dbType) {
        String policyJson = cacheService.getUserPolicy(userId);
        if (policyJson == null || "NO_POLICY".equals(policyJson)) {
            return;
        }

        QueryPolicy policy = JSON.parseObject(policyJson, QueryPolicy.class);
        String upper = sql.toUpperCase();

        if (policy.getAllowJoin() == 0 && upper.contains("JOIN")) {
            throw new RuntimeException("当前策略禁止关联查询(JOIN)");
        }
        if (policy.getAllowSubquery() == 0 && isSubquery(upper)) {
            throw new RuntimeException("当前策略禁止执行子查询");
        }
        if (policy.getAllowAggregation() == 0 && isAggregation(upper)) {
            throw new RuntimeException("当前策略禁止执行聚合统计");
        }

        // 行数限制检查：按方言判断是否存在限制关键字
        if (!hasRowLimit(upper, dbType)) {
            throw new RuntimeException("必须包含行数限制，最大允许 "
                    + policy.getMaxLimit() + " 行");
        }
    }

    /**
     * 判断 SQL 是否包含行数限制，按数据库方言区分
     */
    private boolean hasRowLimit(String upperSql, String dbType) {
        return switch (dbType) {
            case "oracle"    -> upperSql.contains("FETCH FIRST") || upperSql.contains("ROWNUM");
            case "sqlserver" -> upperSql.contains("TOP ");
            default          -> upperSql.contains("LIMIT");
        };
    }

    private boolean isSubquery(String sql) {
        return sql.indexOf("SELECT", 1) > 0;
    }

    private boolean isAggregation(String sql) {
        return sql.contains("COUNT(")
                || sql.contains("SUM(")
                || sql.contains("AVG(")
                || sql.contains("MAX(");
    }
}