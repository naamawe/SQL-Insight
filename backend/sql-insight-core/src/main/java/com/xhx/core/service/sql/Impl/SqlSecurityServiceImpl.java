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
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Set<String> AGG_FUNCTIONS =
            Set.of("COUNT", "SUM", "AVG", "MAX", "MIN");

    @Override
    public void validate(String sql, Long userId, Long dataSourceId) {
        log.info("[安全审计] userId: {}, dsId: {}, sql: {}", userId, dataSourceId, sql);

        if (CommonUtil.isExplain(sql)) {
            log.info("[安全审计] 无权限表，跳过 SQL 审计");
            return;
        }

        try {
            // SQL 只解析一次，后续检查均复用同一个 Statement 对象
            Statement statement = CCJSqlParserUtil.parse(sql);

            TablesNamesFinder finder = new TablesNamesFinder();
            Set<String> tableSet = finder.getTables(statement);
            List<String> tableNames = tableSet.stream()
                    .map(t -> t.contains(".") ? t.substring(t.lastIndexOf(".") + 1) : t)
                    .map(String::toLowerCase)
                    .toList();

            checkTableAccess(userId, dataSourceId, tableNames);

            DataSource dsConfig = dataSourceMapper.selectById(dataSourceId);
            String dbType = dsConfig != null ? dsConfig.getDbType().toLowerCase() : "mysql";

            checkPolicy(userId, statement, sql.toUpperCase(), dbType);

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
     * 对比 Redis 中的策略 JSON；subquery / aggregation 检查基于 AST，不受注释或字符串字面量干扰
     */
    private void checkPolicy(Long userId, Statement statement, String upperSql, String dbType) {
        String policyJson = cacheService.getUserPolicy(userId);
        if (policyJson == null || "NO_POLICY".equals(policyJson)) {
            return;
        }

        QueryPolicy policy = JSON.parseObject(policyJson, QueryPolicy.class);

        if (policy.getAllowJoin() == 0 && upperSql.contains("JOIN")) {
            throw new RuntimeException("当前策略禁止关联查询(JOIN)");
        }
        if (policy.getAllowSubquery() == 0 && isSubquery(statement)) {
            throw new RuntimeException("当前策略禁止执行子查询");
        }
        if (policy.getAllowAggregation() == 0 && isAggregation(statement)) {
            throw new RuntimeException("当前策略禁止执行聚合统计");
        }
        if (!hasRowLimit(upperSql, dbType)) {
            throw new RuntimeException("必须包含行数限制，最大允许 "
                    + policy.getMaxLimit() + " 行");
        }

        // 校验LIMIT值不能超过策略配置的最大值
        int limitValue = extractLimitValue(upperSql, dbType);
        if (limitValue > policy.getMaxLimit()) {
            throw new RuntimeException("LIMIT 值不能超过 " + policy.getMaxLimit() + " 行");
        }
    }

    /**
     * 判断 SQL 是否包含行数限制，按数据库方言区分
     */
    private boolean hasRowLimit(String upperSql, String dbType) {
        return switch (dbType) {
            case "sqlserver" -> upperSql.contains("TOP ");
            default          -> upperSql.contains("LIMIT");
        };
    }

    /**
     * 通过 AST 遍历检测是否含有子查询（EXISTS / IN (SELECT ...) / 派生表等）。
     * JSQLParser 4.6+ 将 SubSelect 重命名为 ParenthesedSelect。
     * 比字符串匹配更可靠，不会被注释或字符串字面量中的 SELECT 关键字干扰。
     */
    private boolean isSubquery(Statement statement) {
        AtomicBoolean found = new AtomicBoolean(false);
        new TablesNamesFinder() {
            @Override
            public void visit(ParenthesedSelect parenthesedSelect) {
                found.set(true);
                super.visit(parenthesedSelect);
            }
        }.getTables(statement);
        return found.get();
    }

    /**
     * 通过 AST 遍历检测是否含有聚合函数（COUNT / SUM / AVG / MAX / MIN）。
     */
    private boolean isAggregation(Statement statement) {
        AtomicBoolean found = new AtomicBoolean(false);
        new TablesNamesFinder() {
            @Override
            public void visit(Function function) {
                if (AGG_FUNCTIONS.contains(function.getName().toUpperCase())) {
                    found.set(true);
                }
                super.visit(function);
            }
        }.getTables(statement);
        return found.get();
    }

    /**
     * 提取SQL中的LIMIT值，支持多种数据库方言
     * @param upperSql 大写的SQL语句
     * @param dbType 数据库类型
     * @return LIMIT值，提取失败返回0
     */
    private int extractLimitValue(String upperSql, String dbType) {
        try {
            switch (dbType) {
                case "mysql":
                case "postgresql":
                    Pattern pattern = Pattern.compile("LIMIT\\s+(\\d+)");
                    Matcher matcher = pattern.matcher(upperSql);
                    if (matcher.find()) {
                        return Integer.parseInt(matcher.group(1));
                    }
                    break;
                case "sqlserver":
                    pattern = Pattern.compile("TOP\\s+(\\d+)");
                    matcher = pattern.matcher(upperSql);
                    if (matcher.find()) {
                        return Integer.parseInt(matcher.group(1));
                    }
                    break;
            }
        } catch (Exception e) {
            log.warn("提取LIMIT值失败: {}", e.getMessage());
        }
        return 0;
    }
}