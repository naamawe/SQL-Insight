package com.xhx.core.service.sql.pipeline;

/**
 * Pipeline Stage 接口，每个实现负责一个处理步骤
 * @author master
 */
public interface PipelineStage {

    void process(GeneratePipelineContext ctx);
}
