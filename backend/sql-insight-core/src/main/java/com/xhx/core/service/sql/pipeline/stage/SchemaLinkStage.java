package com.xhx.core.service.sql.pipeline.stage;

import com.xhx.ai.service.SchemaLinker;
import com.xhx.common.model.TableMetadata;
import com.xhx.core.service.sql.pipeline.GeneratePipelineContext;
import com.xhx.core.service.sql.pipeline.PipelineStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Stage 6：Schema Linking，从全量元数据中筛选与问题相关的表（generate 流程专用）
 * correct 流程跳过此 Stage，直接使用全量元数据
 * @author master
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaLinkStage implements PipelineStage {

    private final SchemaLinker schemaLinker;

    @Override
    public void process(GeneratePipelineContext ctx) {
        if (ctx.isCorrectMode()) {
            // 纠错流程使用全量元数据，不做 Schema Linking
            ctx.setLinkedMetadata(ctx.getAllMetadata());
            return;
        }
        Long dataSourceId = ctx.getSession().getDataSourceId();
        List<TableMetadata> linked = schemaLinker.link(
                ctx.getQuestion(), dataSourceId, ctx.getAllMetadata());
        log.info("Schema Linking：全量 {} 张 → 相关 {} 张",
                ctx.getAllMetadata().size(), linked.size());
        ctx.setLinkedMetadata(linked);
    }
}
