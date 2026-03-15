package com.xhx.core.service.sql.pipeline;

import java.util.List;

/**
 * Pipeline 执行器，按顺序执行所有 Stage
 * @author master
 */
public class SqlGeneratePipeline {

    private final List<PipelineStage> stages;

    public SqlGeneratePipeline(List<PipelineStage> stages) {
        this.stages = stages;
    }

    public void execute(GeneratePipelineContext ctx) {
        for (PipelineStage stage : stages) {
            stage.process(ctx);
        }
    }
}
