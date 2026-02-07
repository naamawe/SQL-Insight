package com.xhx.core.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xhx.ai.service.SqlAssistant;
import com.xhx.core.service.SchemaCollectorService;
import com.xhx.core.service.SqlGeneratorService;
import com.xhx.dal.entity.*;
import com.xhx.dal.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author master
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlGeneratorServiceImpl implements SqlGeneratorService {

    private final UserMapper userMapper;
    private final DataSourceMapper dataSourceMapper;
    private final QueryPolicyMapper queryPolicyMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final TablePermissionMapper tablePermissionMapper;
    private final SqlAssistant sqlAssistant;
    private final SchemaCollectorService schemaCollectorService;

    /**
     * 生成 SQL 的核心逻辑
     */
    @Override
    public String generate(Long userId, Long sessionId, String question) {

        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getId, sessionId)
                        .eq(ChatSession::getUserId, userId)
        );
        if (session == null) {
            log.error("会话不存在或无权访问: sessionId={}, userId={}", sessionId, userId);
            throw new RuntimeException("会话不存在或无权访问");
        }

        Long dataSourceId = session.getDataSourceId();
        // 获取用户信息
        User user = userMapper.selectById(userId);
        if (user == null) {
            log.error("用户不存在: {}", userId);
            throw new RuntimeException("用户不存在");
        }

        // 获取数据源信息
        DataSource dsConfig = dataSourceMapper.selectById(dataSourceId);
        if (dsConfig == null) {
            log.error("数据源不存在: {}", dataSourceId);
            throw new RuntimeException("数据源不存在");
        }

        // 获取用户允许访问的表
        List<TablePermission> permissions = tablePermissionMapper.selectList(
                new LambdaQueryWrapper<TablePermission>()
                        .eq(TablePermission::getRoleId, user.getRoleId())
                        .eq(TablePermission::getDataSourceId, dataSourceId)
        );
        if (permissions.isEmpty()) {
            return "抱歉，您在该数据源下没有任何表的访问权限。";
        }

        List<String> allowedTables = permissions.stream()
                .map(TablePermission::getTableName)
                .toList();

        log.info("用户 {} 正在请求数据源 {}, 授权表数量: {}",
                user.getUserName(), dsConfig.getConnName(), allowedTables.size());

        // 获取查询策略
        QueryPolicy policyEntity = queryPolicyMapper.selectOne(
                new LambdaQueryWrapper<QueryPolicy>().eq(QueryPolicy::getRoleId, user.getRoleId())
        );
        String policyPrompt = formatPolicy(policyEntity);

        // 抓取元数据
        String schemaPrompt = schemaCollectorService.fetchPublicSchema(dsConfig, allowedTables);

        // 调用ai
        log.info("用户 {} 发起请求，应用策略: {}", user.getUserName(), policyPrompt);
        String response = sqlAssistant.chat(sessionId, schemaPrompt, policyPrompt, question);
        return cleanSql(response);
    }


    /**
     * 将策略实体转换为 AI 指令文本
     */
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


    /**
     * 清理 SQL
     * @param response  清理之前的SQL
     * @return  清理之后的SQL
     */
    private String cleanSql(String response) {
        if (response == null) {
            return "";
        }
        return response.replaceAll("(?i)```sql|```", "").trim();
    }
}