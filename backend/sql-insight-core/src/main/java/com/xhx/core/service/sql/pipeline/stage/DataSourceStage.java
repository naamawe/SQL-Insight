package com.xhx.core.service.sql.pipeline.stage;

import com.xhx.common.exception.NotExistException;
import com.xhx.core.service.sql.pipeline.GeneratePipelineContext;
import com.xhx.core.service.sql.pipeline.PipelineStage;
import com.xhx.dal.entity.DataSource;
import com.xhx.dal.mapper.DataSourceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Stage 2：加载数据源配置
 * @author master
 */
@Component
@RequiredArgsConstructor
public class DataSourceStage implements PipelineStage {

    private final DataSourceMapper dataSourceMapper;

    @Override
    public void process(GeneratePipelineContext ctx) {
        Long dataSourceId = ctx.getSession().getDataSourceId();
        DataSource ds = dataSourceMapper.selectById(dataSourceId);
        if (ds == null) {
            throw new NotExistException(404, "数据源配置不存在");
        }
        ctx.setDataSource(ds);
    }
}
