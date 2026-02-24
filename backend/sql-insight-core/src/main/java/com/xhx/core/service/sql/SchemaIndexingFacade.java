package com.xhx.core.service.sql;

import com.xhx.ai.service.VectorSchemaLinker;

import java.util.List;

/**
 * Schema 向量索引编排器（core 模块）
 *
 * <p><b>为什么需要这一层：</b><br>
 * {@code SchemaIndexingService}（ai 模块）只负责写 Qdrant，不知道如何提取元数据。<br>
 * 提取元数据需要 {@code MetadataExtractorRouter}、{@code DynamicDataSourceManager}（core/dal 层）。<br>
 * ai 模块不能反向依赖 core，所以编排层放在 core，向下调用 ai 层写入。
 *
 * <p><b>调用方：</b>
 * <ul>
 *   <li>{@code DataSourceServiceImpl}  — 新增/刷新表名时调 rebuildAsync</li>
 *   <li>{@code CacheEvictEventListener} — 数据源删除时调 deleteIndex</li>
 *   <li>{@code SchemaIndexingBootstrap} — 应用启动时调 rebuildBatch（同步）</li>
 * </ul>
 *
 * @author master
 */
public interface SchemaIndexingFacade {

    /**
     * 异步重建指定数据源的向量索引
     *
     * <p>内部使用 {@code @Async("aiExecutor")}，不阻塞调用方事务。
     * 索引失败只记录日志，不影响主业务（{@link VectorSchemaLinker} 自动降级）。
     *
     * @param dataSourceId 数据源 ID
     */
    void rebuildAsync(Long dataSourceId);

    /**
     * 同步批量重建多个数据源的向量索引
     *
     * <p>仅供应用启动预热使用。同步阻塞直到全部完成，
     * 保证服务就绪时 Qdrant 已有完整数据，避免冷启动时向量检索全部 miss。
     *
     * @param dataSourceIds 数据源 ID 列表
     */
    void rebuildBatch(List<Long> dataSourceIds);

    /**
     * 异步删除指定数据源的所有向量索引
     *
     * <p>数据源删除时调用，防止僵尸向量污染后续检索结果。
     *
     * @param dataSourceId 数据源 ID
     */
    void deleteIndex(Long dataSourceId);
}