package com.xhx.ai.service;

import com.xhx.common.model.TableMetadata;

import java.util.List;

/**
 * Schema 向量索引服务（ai 模块）
 *
 * <p><b>职责边界：</b>
 * 只负责「拿到元数据 → 向量化 → 写/删 Qdrant」，
 * 不关心元数据从哪来、哪些数据源需要建索引。
 * 元数据提取和触发时机的编排由 core 层的 {@code SchemaIndexingFacade} 负责。
 *
 * <p>所有写操作为 upsert 语义，幂等，可重复执行。
 *
 * @author master
 */
public interface SchemaIndexingService {

    /**
     * 确保 Qdrant Collection 存在（幂等，应用启动时调用一次即可）
     *
     * @param vectorSize 向量维度，须与 Embedding 模型保持一致（text-embedding-v3 = 1536）
     * @throws RuntimeException Qdrant 不可达或建库失败时抛出，调用方决定是否继续
     */
    void ensureCollection(int vectorSize);

    /**
     * 将表元数据向量化并写入 Qdrant（upsert，幂等可重复）
     *
     * <p>点 ID = deterministic UUID("dataSourceId:tableName")，
     * 同一张表多次调用结果相同，不产生重复数据。
     *
     * @param dataSourceId 数据源 ID，写入 payload 供检索时按数据源隔离
     * @param tables       已提取完毕的结构化元数据列表
     */
    void upsertTables(Long dataSourceId, List<TableMetadata> tables);

    /**
     * 删除指定数据源的所有向量索引（数据源删除时调用）
     *
     * <p>失败时只记录日志，不抛异常（残留脏数据会被相似度阈值过滤）。
     *
     * @param dataSourceId 数据源 ID
     */
    void deleteByDataSource(Long dataSourceId);
}