package com.xhx.core.service.sql.Impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xhx.common.exception.NotExistException;
import com.xhx.common.exception.ServiceException;
import com.xhx.core.model.TableMetadata;
import com.xhx.core.service.cache.CacheService;
import com.xhx.core.service.cache.PermissionLoader;
import com.xhx.core.service.sql.PromptBuilder;
import com.xhx.core.service.sql.SchemaCollectorService;
import com.xhx.core.service.sql.SchemaLinker;
import com.xhx.core.service.sql.SqlExecutor;
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
 * @author master
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlGeneratorServiceImpl implements SqlGeneratorService {

    private final DataSourceMapper dataSourceMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final SqlExecutor sqlExecutor;
    private final SchemaCollectorService schemaCollectorService;
    private final SchemaLinker schemaLinker;
    private final PromptBuilder promptBuilder;
    private final SqlSecurityService sqlSecurityService;
    private final PermissionLoader permissionLoader;
    private final CacheService cacheService;
    private final UserMapper userMapper;

    @Override
    public String generate(Long userId, Long sessionId, String question) {
        GenerateContext ctx = buildContext(userId, sessionId, question);

        // 调用 AI 生成 SQL
        log.info("调用 AI，userId: {}, dbType: {}, 相关表: {}",
                userId, ctx.dbType(),
                ctx.linkedMetadata().stream().map(TableMetadata::getTableName).toList());
        String response = sqlExecutor.execute(sessionId, ctx.systemPrompt(), question);
        String cleanedSql = cleanSql(response);

        // 安全校验
        validateSql(cleanedSql, userId, ctx.dataSourceId());

        return cleanedSql;
    }

    @Override
    public String correct(Long userId, Long sessionId, String errorMessage, String wrongSql) {
        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getId, sessionId)
                        .eq(ChatSession::getUserId, userId));
        if (session == null) {
            throw new NotExistException(404, "会话不存在或无权访问");
        }

        DataSource dsConfig = dataSourceMapper.selectById(session.getDataSourceId());
        if (dsConfig == null) {
            throw new NotExistException(404, "数据源配置不存在");
        }

        Long roleId = getRoleId(userId);
        String systemPrompt = buildSystemPrompt(userId, roleId, session.getDataSourceId(), dsConfig);

        // 调用 AI 纠错
        String response = sqlExecutor.executeWithCorrection(
                sessionId, systemPrompt, errorMessage, wrongSql);
        String cleanedSql = cleanSql(response);

        // 纠错后同样需要安全校验
        validateSql(cleanedSql, userId, session.getDataSourceId());

        return cleanedSql;
    }

    // ==================== 私有工具方法 ====================

    /**
     * 构造生成 SQL 所需的完整上下文
     */
    private GenerateContext buildContext(Long userId, Long sessionId, String question) {
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

        Long roleId = getRoleId(userId);

        Set<String> allPerms = permissionLoader.loadPermissions(userId, roleId);
        if (allPerms.isEmpty()) {
            throw new ServiceException("您当前没有任何表的访问权限，请联系管理员授权。");
        }

        List<String> allowedTables = allPerms.stream()
                .filter(p -> p.startsWith(dataSourceId + ":"))
                .map(p -> p.split(":", 3)[1])
                .toList();

        if (allowedTables.isEmpty()) {
            throw new ServiceException("您在该数据源下没有已授权的表，请联系管理员配置权限。");
        }

        List<TableMetadata> allMetadata = schemaCollectorService.getMetadata(dsConfig, allowedTables);
        List<TableMetadata> linkedMetadata = schemaLinker.link(question, allMetadata);
        log.info("Schema Linking：全量 {} 张表 → 相关 {} 张表",
                allMetadata.size(), linkedMetadata.size());

        String systemPrompt = buildSystemPrompt(userId, roleId, dsConfig,
                linkedMetadata);

        return new GenerateContext(dataSourceId, dsConfig.getDbType(), linkedMetadata, systemPrompt);
    }

    /**
     * 构造 systemPrompt（generate 流程使用，需要传入 linkedMetadata）
     */
    private String buildSystemPrompt(Long userId, Long roleId,
                                     DataSource dsConfig, List<TableMetadata> linkedMetadata) {
        String schemaText = schemaCollectorService.format(linkedMetadata);
        String policyJson = permissionLoader.loadPolicy(userId, roleId);
        QueryPolicy policyEntity = policyJson != null
                ? JSON.parseObject(policyJson, QueryPolicy.class) : null;
        String policyText = formatPolicy(policyEntity);
        return promptBuilder.build(dsConfig.getDbType(), schemaText, policyText);
    }

    /**
     * 构造 systemPrompt（correct 流程使用，重新加载全量 schema）
     */
    private String buildSystemPrompt(Long userId, Long roleId, Long dataSourceId,
                                     DataSource dsConfig) {
        Set<String> allPerms = permissionLoader.loadPermissions(userId, roleId);
        List<String> allowedTables = allPerms.stream()
                .filter(p -> p.startsWith(dataSourceId + ":"))
                .map(p -> p.split(":", 3)[1])
                .toList();

        List<TableMetadata> allMetadata = schemaCollectorService.getMetadata(dsConfig, allowedTables);

        // correct 时不再做 Schema Linking，传全量 schema 给 AI，确保纠错信息完整
        return buildSystemPrompt(userId, roleId, dsConfig, allMetadata);
    }

    private void validateSql(String sql, Long userId, Long dataSourceId) {
        try {
            sqlSecurityService.validate(sql, userId, dataSourceId);
        } catch (Exception e) {
            log.warn("SQL 安全校验未通过，userId={}, reason={}", userId, e.getMessage());
            throw new ServiceException(400, "SQL 校验未通过: " + e.getMessage());
        }
    }

    private Long getRoleId(Long userId) {
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

    private String formatPolicy(QueryPolicy policy) {
        if (policy == null) {
            return "请生成标准的 SQL。";
        }
        StringBuilder sb = new StringBuilder("必须严格遵守以下查询约束：\n");
        sb.append(String.format("- SELECT 语句必须包含行数限制，且最大不能超过 %d 行。\n",
                policy.getMaxLimit()));
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
        String cleaned = response.replaceAll("(?i)```sql|```", "").trim();
        // 兜底：如果 AI 返回了多条 SQL，只取第一条
        int semicolon = cleaned.indexOf(';');
        if (semicolon > 0) {
            cleaned = cleaned.substring(0, semicolon).trim();
        }
        return cleaned;
    }

    /**
     * generate 流程的上下文数据载体
     */
    private record GenerateContext(
            Long dataSourceId,
            String dbType,
            List<TableMetadata> linkedMetadata,
            String systemPrompt
    ) {}
}