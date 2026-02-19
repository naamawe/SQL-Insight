package com.xhx.core.service.sql.Impl;

import com.alibaba.fastjson2.JSON;
import com.xhx.common.constant.SecurityConstants;
import com.xhx.core.service.sql.SqlSecurityService;
import com.xhx.dal.entity.QueryPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SQL 安全校验服务
 * @author master
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlSecurityServiceImpl implements SqlSecurityService {

    private final StringRedisTemplate redisTemplate;

    private static final String PERMISSION_SELECT = "SELECT";

    @Override
    public void validate(String sql, Long userId, Long dataSourceId) {
        log.info("==> [安全审计] 用户ID: {}, 数据源: {}, 原始SQL: {}", userId, dataSourceId, sql);

        try {
            Statement statement = CCJSqlParserUtil.parse(sql);

            TablesNamesFinder tablesNamesFinder = new TablesNamesFinder();
            List<String> tableNames = tablesNamesFinder.getTableList(statement);

            checkTableAccess(userId, dataSourceId, tableNames);
            checkPolicy(userId, sql);

        } catch (Exception e) {
            log.error("==> [审计未通过] 原因: {}", e.getMessage());
            throw new RuntimeException("SQL 安全风险拦截: " + e.getMessage());
        }
    }

    /**
     * 对比 Redis 中的表权限快照
     * Redis 中存储格式为：{dataSourceId}:{tableName}:{PERMISSION}，例如 "1:orders:SELECT"
     */
    private void checkTableAccess(Long userId, Long dataSourceId, List<String> tableNames) {
        String redisKey = SecurityConstants.USER_PERMISSION_KEY + userId;

        for (String tableName : tableNames) {
            String requiredPerm = dataSourceId + ":" + tableName + ":" + PERMISSION_SELECT;

            Boolean isAllowed = redisTemplate.opsForSet().isMember(redisKey, requiredPerm);

            if (Boolean.FALSE.equals(isAllowed)) {
                throw new RuntimeException("无权访问表: " + tableName);
            }
        }
    }

    /**
     * 对比 Redis 中的策略 JSON
     */
    private void checkPolicy(Long userId, String sql) {
        String policyKey = SecurityConstants.USER_POLICY_KEY + userId;
        String policyJson = redisTemplate.opsForValue().get(policyKey);

        if (policyJson == null) {
            return;
        }
        QueryPolicy policy = JSON.parseObject(policyJson, QueryPolicy.class);

        String upperSql = sql.toUpperCase();

        if (policy.getAllowJoin() == 0 && (upperSql.contains("JOIN") || upperSql.contains(","))) {
            throw new RuntimeException("当前策略禁止关联查询(JOIN)");
        }

        if (policy.getAllowSubquery() == 0 && isSubquery(upperSql)) {
            throw new RuntimeException("当前策略禁止执行子查询");
        }

        if (policy.getAllowAggregation() == 0 && isAggregation(upperSql)) {
            throw new RuntimeException("当前策略禁止执行聚合统计(COUNT/SUM等)");
        }

        if (!upperSql.contains("LIMIT")) {
            throw new RuntimeException("必须包含 LIMIT 限制，最大允许 " + policy.getMaxLimit() + " 行");
        }
    }

    private boolean isSubquery(String sql) {
        return sql.indexOf("SELECT", 1) > 0;
    }

    private boolean isAggregation(String sql) {
        return sql.contains("COUNT(") || sql.contains("SUM(") || sql.contains("AVG(") || sql.contains("MAX(");
    }
}