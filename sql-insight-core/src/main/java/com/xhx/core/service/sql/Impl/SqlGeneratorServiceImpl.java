package com.xhx.core.service.sql.Impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xhx.ai.service.SqlAssistant;
import com.xhx.common.constant.SecurityConstants;
import com.xhx.core.service.sql.SchemaCollectorService;
import com.xhx.core.service.sql.SqlGeneratorService;
import com.xhx.core.service.sql.SqlSecurityService;
import com.xhx.dal.entity.*;
import com.xhx.dal.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final StringRedisTemplate redisTemplate;

    /**
     * 生成 SQL 的核心逻辑
     */
    @Override
    public String generate(Long userId, Long sessionId, String question) {
        // 1. 基础校验：获取会话并确认数据源
        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getId, sessionId)
                        .eq(ChatSession::getUserId, userId)
        );
        if (session == null) {
            throw new RuntimeException("会话不存在或无权访问");
        }

        Long dataSourceId = session.getDataSourceId();
        DataSource dsConfig = dataSourceMapper.selectById(dataSourceId);
        if (dsConfig == null) {
            throw new RuntimeException("数据源配置已失效");
        }

        String permKey = SecurityConstants.USER_PERMISSION_KEY + userId;
        Set<String> allPerms = redisTemplate.opsForSet().members(permKey);

        if (CollectionUtils.isEmpty(allPerms)) {
            return "抱歉，您当前没有任何表的访问权限。";
        }

        // 过滤出当前数据源下的表名
        List<String> allowedTables = allPerms.stream()
                .filter(p -> p.startsWith(dataSourceId + ":"))
                .map(p -> p.split(":")[1])
                .collect(Collectors.toList());

        if (allowedTables.isEmpty()) {
            return "抱歉，您在该数据源下没有已授权的表。";
        }

        String policyKey = SecurityConstants.USER_POLICY_KEY + userId;
        String policyJson = redisTemplate.opsForValue().get(policyKey);
        QueryPolicy policyEntity = JSON.parseObject(policyJson, QueryPolicy.class);

        // 采集元数据并调用 AI
        String schemaPrompt = schemaCollectorService.fetchPublicSchema(dsConfig, allowedTables);
        String policyPrompt = formatPolicy(policyEntity);

        log.info("用户 {} 发起提问，正在调用 AI 生成 SQL...", userId);
        String response = sqlAssistant.chat(sessionId, schemaPrompt, policyPrompt, question);

        // 清理 SQL
        String cleanedSql = cleanSql(response);

        // 安全校验
        sqlSecurityService.validate(cleanedSql, userId, dataSourceId);

        log.info("SQL 生成并校验通过: {}", cleanedSql);
        return cleanedSql;
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
     * @param response  清理之前的 SQL
     * @return  清理之后的 SQL
     */
    private String cleanSql(String response) {
        if (response == null) {
            return "";
        }
        return response.replaceAll("(?i)```sql|```", "").trim();
    }
}