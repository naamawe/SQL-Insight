package com.xhx.core.service.sql.Impl;

import com.xhx.ai.service.SchemaIndexingService;
import com.xhx.common.model.TableMetadata;
import com.xhx.core.extractor.MetadataExtractorRouter;
import com.xhx.core.service.management.DataSourceService;
import com.xhx.core.service.sql.SchemaIndexingFacade;
import com.xhx.dal.config.DynamicDataSourceManager;
import com.xhx.dal.entity.DataSource;
import com.xhx.dal.mapper.DataSourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Schema 向量索引编排器实现
 *
 * <p><b>doRebuild 核心流程：</b>
 * <ol>
 *   <li>查询数据源配置（DataSourceMapper）</li>
 *   <li>获取所有表名（DataSourceService，优先走 Redis 缓存）</li>
 *   <li>连接目标库提取完整元数据（MetadataExtractorRouter）</li>
 *   <li>委托 SchemaIndexingService 向量化并 upsert 到 Qdrant</li>
 * </ol>
 *
 * <p><b>容错策略：</b><br>
 * 单个数据源失败时记录日志并继续，不整体中断。<br>
 * 索引失败对用户无感知：VectorSchemaLinker 自动降级 KeywordSchemaLinker。
 *
 * @author master
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaIndexingFacadeImpl implements SchemaIndexingFacade {

    private final DataSourceMapper         dataSourceMapper;
    private final DataSourceService        dataSourceService;
    private final DynamicDataSourceManager dataSourceManager;
    private final MetadataExtractorRouter  metadataExtractorRouter;
    private final SchemaIndexingService    schemaIndexingService;

    @Async("aiExecutor")
    @Override
    public void rebuildAsync(Long dataSourceId) {
        log.info("[IndexFacade] 异步重建数据源 {} 向量索引", dataSourceId);
        try {
            doRebuild(dataSourceId);
        } catch (Exception e) {
            log.error("[IndexFacade] 数据源 {} 索引重建失败，SchemaLinker 将降级关键词匹配: {}",
                    dataSourceId, e.getMessage());
        }
    }

    @Override
    public void rebuildBatch(List<Long> dataSourceIds) {
        log.info("[IndexFacade] 批量重建，共 {} 个数据源", dataSourceIds.size());
        int success = 0;
        for (Long dsId : dataSourceIds) {
            try {
                doRebuild(dsId);
                success++;
            } catch (Exception e) {
                log.error("[IndexFacade] 数据源 {} 重建失败，跳过: {}", dsId, e.getMessage());
            }
        }
        log.info("[IndexFacade] 批量重建完成：{}/{} 成功", success, dataSourceIds.size());
    }

    @Async("aiExecutor")
    @Override
    public void deleteIndex(Long dataSourceId) {
        log.info("[IndexFacade] 异步删除数据源 {} 向量索引", dataSourceId);
        schemaIndexingService.deleteByDataSource(dataSourceId);
    }

    // ==================== 核心流程 ====================

    private void doRebuild(Long dataSourceId) {
        DataSource dsConfig = dataSourceMapper.selectById(dataSourceId);
        if (dsConfig == null) {
            log.warn("[IndexFacade] 数据源 {} 不存在，跳过", dataSourceId);
            return;
        }

        // 优先走 Redis 缓存，命中率极高，避免反复连接目标库查表名
        List<String> tableNames = dataSourceService.getTableNames(dataSourceId);
        if (tableNames.isEmpty()) {
            log.warn("[IndexFacade] 数据源 {} [{}] 无表，跳过",
                    dataSourceId, dsConfig.getConnName());
            return;
        }

        log.info("[IndexFacade] 提取数据源 {} [{}] 元数据，共 {} 张表",
                dataSourceId, dsConfig.getConnName(), tableNames.size());

        List<TableMetadata> metadata = extractMetadata(dsConfig, tableNames);
        schemaIndexingService.upsertTables(dataSourceId, metadata);
    }

    private List<TableMetadata> extractMetadata(DataSource dsConfig,
                                                 List<String> tableNames) {
        javax.sql.DataSource ds = dataSourceManager.getDataSource(dsConfig);
        try (Connection conn = ds.getConnection()) {
            return metadataExtractorRouter.extract(dsConfig.getDbType(), conn, tableNames);
        } catch (SQLException e) {
            throw new RuntimeException(
                    "连接数据源 [" + dsConfig.getConnName() + "] 失败: " + e.getMessage(), e);
        }
    }
}