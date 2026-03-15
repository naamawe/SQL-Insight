package com.xhx.core.service.sql.pipeline.stage;

import com.xhx.common.exception.ServiceException;
import com.xhx.core.service.sql.SqlSecurityService;
import com.xhx.core.service.sql.pipeline.GeneratePipelineContext;
import com.xhx.core.service.sql.pipeline.PipelineStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stage 9：SQL 安全校验（explain 类响应跳过）
 * @author master
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqlValidationStage implements PipelineStage {

    private final SqlSecurityService sqlSecurityService;

    @Override
    public void process(GeneratePipelineContext ctx) {
        if (ctx.getAiResponse().isExplain()) {
            return;
        }
        try {
            sqlSecurityService.validate(
                    ctx.getAiResponse().cleanSql(),
                    ctx.getUserId(),
                    ctx.getSession().getDataSourceId());
        } catch (Exception e) {
            log.warn("SQL 安全校验未通过，userId={}, reason={}", ctx.getUserId(), e.getMessage());
            throw new ServiceException(400, "SQL 校验未通过: " + e.getMessage());
        }
    }
}
