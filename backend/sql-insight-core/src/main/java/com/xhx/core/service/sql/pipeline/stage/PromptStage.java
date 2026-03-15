package com.xhx.core.service.sql.pipeline.stage;

import com.alibaba.fastjson2.JSON;
import com.xhx.ai.service.PromptBuilder;
import com.xhx.core.service.cache.PermissionLoader;
import com.xhx.core.service.sql.SchemaCollectorService;
import com.xhx.core.service.sql.pipeline.GeneratePipelineContext;
import com.xhx.core.service.sql.pipeline.PipelineStage;
import com.xhx.dal.entity.QueryPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Stage 7：构建 System Prompt
 * @author master
 */
@Component
@RequiredArgsConstructor
public class PromptStage implements PipelineStage {

    private final SchemaCollectorService schemaCollectorService;
    private final PermissionLoader permissionLoader;
    private final PromptBuilder promptBuilder;

    @Override
    public void process(GeneratePipelineContext ctx) {
        String schemaText = schemaCollectorService.format(ctx.getLinkedMetadata());
        String policyJson = permissionLoader.loadPolicy(ctx.getUserId(), ctx.getRoleId());
        QueryPolicy policy = policyJson != null
                ? JSON.parseObject(policyJson, QueryPolicy.class) : null;
        String systemPrompt = promptBuilder.build(
                ctx.getDataSource().getDbType(), schemaText, formatPolicy(policy));
        ctx.setSystemPrompt(systemPrompt);
    }

    private String formatPolicy(QueryPolicy policy) {
        if (policy == null) {
            return "请生成标准的 SQL。";
        }
        return "必须严格遵守以下查询约束：\n" + String.format(
                "- SELECT 语句必须包含行数限制，最大不超过 %d 行。\n", policy.getMaxLimit()) +
                (policy.getAllowJoin() == 0
                        ? "- 禁止使用 JOIN 多表关联查询。\n"
                        : "- 允许使用 JOIN 多表关联查询。\n") +
                (policy.getAllowSubquery() == 0
                        ? "- 禁止使用子查询。\n"
                        : "- 允许使用子查询。\n") +
                (policy.getAllowAggregation() == 0
                        ? "- 禁止使用聚合函数（SUM、AVG、COUNT、GROUP BY）。\n"
                        : "- 允许使用聚合函数进行统计分析。\n");
    }
}
