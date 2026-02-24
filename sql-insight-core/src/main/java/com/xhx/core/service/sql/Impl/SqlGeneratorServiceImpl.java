package com.xhx.core.service.sql.Impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xhx.ai.model.AiResponse;
import com.xhx.ai.service.PromptBuilder;
import com.xhx.ai.service.SchemaLinker;
import com.xhx.ai.service.SqlExecutor;
import com.xhx.common.exception.NotExistException;
import com.xhx.common.exception.ServiceException;
import com.xhx.common.model.TableMetadata;
import com.xhx.core.service.cache.CacheService;
import com.xhx.core.service.cache.PermissionLoader;
import com.xhx.core.service.sql.SchemaCollectorService;
import com.xhx.core.service.sql.SqlGeneratorService;
import com.xhx.core.service.sql.SqlSecurityService;
import com.xhx.dal.entity.ChatSession;
import com.xhx.dal.entity.DataSource;
import com.xhx.dal.entity.QueryPolicy;
import com.xhx.dal.entity.User;
import com.xhx.dal.mapper.ChatSessionMapper;
import com.xhx.dal.mapper.DataSourceMapper;
import com.xhx.dal.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * SQL 生成服务实现
 * @author master
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlGeneratorServiceImpl implements SqlGeneratorService {

    private final DataSourceMapper       dataSourceMapper;
    private final ChatSessionMapper      chatSessionMapper;
    private final SqlExecutor            sqlExecutor;
    private final PromptBuilder          promptBuilder;
    private final SchemaLinker           schemaLinker;
    private final SchemaCollectorService schemaCollectorService;
    private final SqlSecurityService     sqlSecurityService;
    private final PermissionLoader       permissionLoader;
    private final CacheService           cacheService;
    private final UserMapper             userMapper;

    @Override
    public String generate(Long userId, Long sessionId, String question) {
        GenerateContext ctx = buildContext(userId, sessionId, question);

        AiResponse response = sqlExecutor.execute(sessionId, ctx.systemPrompt(), question);

        if (!response.isExplain()) {
            validateSql(response.cleanSql(), userId, ctx.dataSourceId());
        }

        return response.cleanSql();
    }

    @Override
    public String correct(Long userId, Long sessionId, String errorMessage, String wrongSql) {
        ChatSession session = requireSession(userId, sessionId);
        DataSource dsConfig = requireDataSource(session.getDataSourceId());

        Long roleId = resolveRoleId(userId);
        String systemPrompt = buildSystemPrompt(
                userId, roleId, dsConfig,
                fullMetadata(userId, roleId, session.getDataSourceId(), dsConfig));

        AiResponse response = sqlExecutor.executeWithCorrection(sessionId, systemPrompt, errorMessage, wrongSql);

        if (!response.isExplain()) {
            validateSql(response.cleanSql(), userId, session.getDataSourceId());
        }

        return response.cleanSql();
    }

    // ==================== 私有方法 ====================

    private GenerateContext buildContext(Long userId, Long sessionId, String question) {
        ChatSession session = requireSession(userId, sessionId);
        Long dataSourceId = session.getDataSourceId();
        DataSource dsConfig = requireDataSource(dataSourceId);
        Long roleId = resolveRoleId(userId);

        List<TableMetadata> allMeta = fullMetadata(userId, roleId, dataSourceId, dsConfig);

        //向量检索按数据源隔离
        List<TableMetadata> linked = schemaLinker.link(question, dataSourceId, allMeta);

        log.info("Schema Linking：全量 {} 张 → 相关 {} 张", allMeta.size(), linked.size());

        String systemPrompt = buildSystemPrompt(userId, roleId, dsConfig, linked);
        return new GenerateContext(dsConfig.getDbType(), dataSourceId, systemPrompt, linked);
    }

    private List<TableMetadata> fullMetadata(Long userId, Long roleId,
                                             Long dataSourceId, DataSource dsConfig) {
        List<String> allowedTables = resolveAllowedTables(userId, roleId, dataSourceId);
        return schemaCollectorService.getMetadata(dsConfig, allowedTables);
    }

    private String buildSystemPrompt(Long userId, Long roleId,
                                     DataSource dsConfig, List<TableMetadata> metadata) {
        String schemaText = schemaCollectorService.format(metadata);
        String policyJson = permissionLoader.loadPolicy(userId, roleId);
        QueryPolicy policy = policyJson != null
                ? JSON.parseObject(policyJson, QueryPolicy.class) : null;
        return promptBuilder.build(dsConfig.getDbType(), schemaText, formatPolicy(policy));
    }

    private List<String> resolveAllowedTables(Long userId, Long roleId, Long dataSourceId) {
        Set<String> allPerms = permissionLoader.loadPermissions(userId, roleId);
        if (allPerms.isEmpty()) {
            throw new ServiceException("您当前没有任何表的访问权限，请联系管理员授权。");
        }
        List<String> allowed = allPerms.stream()
                .filter(p -> p.startsWith(dataSourceId + ":"))
                .map(p -> p.split(":", 3)[1])
                .toList();
        if (allowed.isEmpty()) {
            throw new ServiceException("您在该数据源下没有已授权的表，请联系管理员配置权限。");
        }
        return allowed;
    }

    private void validateSql(String sql, Long userId, Long dataSourceId) {
        try {
            sqlSecurityService.validate(sql, userId, dataSourceId);
        } catch (Exception e) {
            log.warn("SQL 安全校验未通过，userId={}, reason={}", userId, e.getMessage());
            throw new ServiceException(400, "SQL 校验未通过: " + e.getMessage());
        }
    }

    private Long resolveRoleId(Long userId) {
        Long roleId = cacheService.getUserRoleId(userId);
        if (roleId != null) {
            return roleId;
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new NotExistException(404, "用户不存在");
        }
        cacheService.putUserRoleId(userId, user.getRoleId());
        return user.getRoleId();
    }

    private ChatSession requireSession(Long userId, Long sessionId) {
        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getId, sessionId)
                        .eq(ChatSession::getUserId, userId));
        if (session == null) {
            throw new NotExistException(404, "会话不存在或无权访问");
        }
        return session;
    }

    private DataSource requireDataSource(Long dataSourceId) {
        DataSource ds = dataSourceMapper.selectById(dataSourceId);
        if (ds == null) {
            throw new NotExistException(404, "数据源配置不存在");
        }
        return ds;
    }

    private String formatPolicy(QueryPolicy policy) {
        if (policy == null) {
            return "请生成标准的 SQL。";
        }
        StringBuilder sb = new StringBuilder("必须严格遵守以下查询约束：\n");
        sb.append(String.format(
                "- SELECT 语句必须包含行数限制，最大不超过 %d 行。\n", policy.getMaxLimit()));
        if (policy.getAllowJoin() == 0) {
            sb.append("- 禁止使用 JOIN 多表关联查询。\n");
        }
        if (policy.getAllowSubquery() == 0) {
            sb.append("- 禁止使用子查询。\n");
        }
        if (policy.getAllowAggregation() == 0) {
            sb.append("- 禁止使用聚合函数（SUM、AVG、COUNT、GROUP BY）。\n");
        } else {
            sb.append("- 允许使用聚合函数进行统计分析。\n");
        }
        return sb.toString();
    }


    private record GenerateContext(
            String dbType,
            Long dataSourceId,
            String systemPrompt,
            List<TableMetadata> linkedMetadata
    ) {}
}