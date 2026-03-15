package com.xhx.core.service.sql.pipeline.stage;

import com.xhx.common.model.TableMetadata;
import com.xhx.core.service.sql.SchemaCollectorService;
import com.xhx.core.service.sql.pipeline.GeneratePipelineContext;
import com.xhx.core.service.sql.pipeline.PipelineStage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Stage 5：获取数据源元数据（按权限过滤后的表）
 * @author master
 */
@Component
@RequiredArgsConstructor
public class MetadataStage implements PipelineStage {

    private final SchemaCollectorService schemaCollectorService;

    @Override
    public void process(GeneratePipelineContext ctx) {
        List<TableMetadata> allMeta = schemaCollectorService.getMetadata(
                ctx.getDataSource(), ctx.getAllowedTables());
        ctx.setAllMetadata(allMeta);
    }
}
