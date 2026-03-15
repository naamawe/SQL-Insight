package com.xhx.core.service.sql.pipeline.stage;

import com.xhx.ai.model.AiResponse;
import com.xhx.ai.service.SqlExecutor;
import com.xhx.core.service.sql.pipeline.GeneratePipelineContext;
import com.xhx.core.service.sql.pipeline.PipelineStage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Stage 8：调用 LLM 生成或纠正 SQL
 * @author master
 */
@Component
@RequiredArgsConstructor
public class LlmStage implements PipelineStage {

    private final SqlExecutor sqlExecutor;

    @Override
    public void process(GeneratePipelineContext ctx) {
        AiResponse response;
        if (ctx.isCorrectMode()) {
            response = sqlExecutor.executeWithCorrection(
                    ctx.getSessionId(), ctx.getSystemPrompt(),
                    ctx.getErrorMessage(), ctx.getWrongSql());
        } else {
            response = sqlExecutor.execute(
                    ctx.getSessionId(), ctx.getSystemPrompt(), ctx.getQuestion());
        }
        ctx.setAiResponse(response);
    }
}
