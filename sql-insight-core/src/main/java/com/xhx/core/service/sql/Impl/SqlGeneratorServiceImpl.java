package com.xhx.core.service.sql.Impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xhx.ai.service.SqlAssistant;
import com.xhx.common.exception.NotExistException;
import com.xhx.common.exception.ServiceException;
import com.xhx.core.service.cache.CacheService;
import com.xhx.core.service.cache.PermissionLoader;
import com.xhx.core.service.sql.SchemaCollectorService;
import com.xhx.core.service.sql.SqlGeneratorService;
import com.xhx.core.service.sql.SqlSecurityService;
import com.xhx.dal.entity.*;
import com.xhx.dal.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * @author master
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlGeneratorServiceImpl implements SqlGeneratorService {

    private final DataSourceMapper dataSourceMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final SqlAssistant sqlAssistant;
    private final SchemaCollectorService schemaCollectorService;
    private final SqlSecurityService sqlSecurityService;
    private final PermissionLoader permissionLoader;
    private final CacheService cacheService;
    private final UserMapper userMapper;

    @Override
    public String generate(Long userId, Long sessionId, String question) {
        // 校验 session
        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getId, sessionId)
                        .eq(ChatSession::getUserId, userId));
        if (session == null) {
            throw new NotExistException(404, "会话不存在或无权访问");
        }

        Long dataSourceId = session.getDataSourceId();
        DataSource dsConfig = dataSourceMapper.selectById(dataSourceId);
        if (dsConfig == null) {
            throw new NotExistException(404, "数据源配置不存在");
        }

        // 获取 roleId（先查缓存，再查DB）
        Long roleId = getRoleId(userId);

        // 懒加载表权限（带分布式锁防击穿）
        Set<String> allPerms = permissionLoader.loadPermissions(userId, roleId);

        if (allPerms.isEmpty()) {
            return "抱歉，您当前没有任何表的访问权限，请联系管理员授权。";
        }

        // 过滤当前数据源的可用表
        List<String> allowedTables = allPerms.stream()
                .filter(p -> p.startsWith(dataSourceId + ":"))
                .map(p -> p.split(":")[1])
                .toList();

        if (allowedTables.isEmpty()) {
            return "抱歉，您在该数据源下没有已授权的表，请联系管理员配置权限。";
        }

        // 懒加载策略
        String policyJson = permissionLoader.loadPolicy(userId, roleId);
        QueryPolicy policyEntity = policyJson != null
                ? JSON.parseObject(policyJson, QueryPolicy.class) : null;

        String schemaPrompt = schemaCollectorService.fetchPublicSchema(dsConfig, allowedTables);
        String policyPrompt = formatPolicy(policyEntity);

        log.info("用户 {} 发起提问，调用 AI 生成 SQL", userId);
        String response = sqlAssistant.chat(sessionId, schemaPrompt, policyPrompt, question);
        String cleanedSql = cleanSql(response);

        try {
            sqlSecurityService.validate(cleanedSql, userId, dataSourceId);
        } catch (Exception e) {
            log.warn("SQL 安全校验未通过，userId={}, reason={}", userId, e.getMessage());
            throw new ServiceException(400, "SQL 校验未通过: " + e.getMessage());
        }

        return cleanedSql;
    }

    /** 获取用户 roleId，先查缓存再查DB */
    private Long getRoleId(Long userId) {
        Long roleId = cacheService.getUserRoleId(userId);
        if (roleId != null) {
            return roleId;
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new NotExistException(404, "用户不存在");
        }
        // 回填缓存
        cacheService.putUserRoleId(userId, user.getRoleId());
        return user.getRoleId();
    }

    private String formatPolicy(QueryPolicy policy) {
        if (policy == null) {
            return "请生成标准的 SQL。";
        }

        StringBuilder sb = new StringBuilder("必须严格遵守以下查询约束：\n");
        sb.append(String.format("- 如果是 SELECT 语句，必须包含 LIMIT，且最大不能超过 %d 行。\n", policy.getMaxLimit()));

        if (policy.getAllowJoin() == 0) {
            sb.append("- 禁止使用 JOIN 进行多表关联查询。\n");
        }
        if (policy.getAllowSubquery() == 0) {
            sb.append("- 禁止使用子查询。\n");
        }
        if (policy.getAllowAggregation() == 0) {
            sb.append("- 禁止使用聚合函数（如 SUM, AVG, COUNT, GROUP BY）。\n");
        } else {
            sb.append("- 允许使用聚合函数进行统计分析。\n");
        }

        return sb.toString();
    }

    private String cleanSql(String response) {
        if (response == null) {
            return "";
        }
        return response.replaceAll("(?i)```sql|```", "").trim();
    }
}