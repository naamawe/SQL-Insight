package com.xhx.core.service.sql.Impl;

import com.xhx.ai.model.AiResponse;
import com.xhx.core.service.sql.SqlGeneratorService;
import com.xhx.core.service.sql.pipeline.GeneratePipelineContext;
import com.xhx.core.service.sql.pipeline.SqlGeneratePipeline;
import com.xhx.core.service.sql.pipeline.stage.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * SQL 生成服务实现，基于 Pipeline 模式组装处理流程
 * @author master
 */
@Service
@RequiredArgsConstructor
public class SqlGeneratorServiceImpl implements SqlGeneratorService {

    private final SessionStage       sessionStage;
    private final DataSourceStage    dataSourceStage;
    private final RoleStage          roleStage;
    private final PermissionStage    permissionStage;
    private final MetadataStage      metadataStage;
    private final SchemaLinkStage    schemaLinkStage;
    private final PromptStage        promptStage;
    private final LlmStage           llmStage;
    private final SqlValidationStage sqlValidationStage;

    @Override
    public String generate(Long userId, Long sessionId, String question) {
        GeneratePipelineContext ctx = GeneratePipelineContext.forGenerate(userId, sessionId, question);
        buildPipeline().execute(ctx);
        return ctx.getAiResponse().cleanSql();
    }

    @Override
    public AiResponse correct(Long userId, Long sessionId, String errorMessage, String wrongSql) {
        GeneratePipelineContext ctx = GeneratePipelineContext.forCorrect(userId, sessionId, errorMessage, wrongSql);
        buildPipeline().execute(ctx);
        return ctx.getAiResponse();
    }

    private SqlGeneratePipeline buildPipeline() {
        return new SqlGeneratePipeline(List.of(
                sessionStage,
                dataSourceStage,
                roleStage,
                permissionStage,
                metadataStage,
                schemaLinkStage,
                promptStage,
                llmStage,
                sqlValidationStage
        ));
    }
}