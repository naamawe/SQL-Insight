package com.xhx.ai.service;

import com.xhx.ai.config.QdrantProperties;
import com.xhx.common.model.ColumnMetadata;
import com.xhx.common.model.TableMetadata;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Points;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;

/**
 * Schema 向量索引服务实现
 *
 * <p><b>语义文本构建策略：</b><br>
 * 格式：「表名 [表注释] 字段名1 [字段注释1] 字段名2 [字段注释2] …」<br>
 * 空白注释和占位注释（"(未命名注释)"）不进入语义文本，减少向量空间噪音。
 *
 * <p><b>点 ID 策略：</b><br>
 * {@code UUID.nameUUIDFromBytes("dataSourceId:tableName")} 生成确定性 UUID。<br>
 * 同一张表无论重建多少次，ID 始终相同，配合 upsert 保证幂等无重复数据。
 *
 * <p><b>批处理：</b><br>
 * 每批 {@code BATCH_SIZE} 张表，单张向量化失败时跳过并记录，不影响整批其他表。
 *
 * @author master
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaIndexingServiceImpl implements SchemaIndexingService {

    private final QdrantClient           qdrantClient;
    private final AliyunEmbeddingService embeddingService;
    private final QdrantProperties       properties;

    private static final int  BATCH_SIZE             = 20;
    private static final long QDRANT_TIMEOUT_SECONDS = 10L;
    private static final String PLACEHOLDER_COMMENT  = "(未命名注释)";

    private final ExecutorService indexExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r);
        t.setName("qdrant-index-worker-" + t.getId());
        return t;
    });
    // ==================== public API ====================

    @Override
    public void ensureCollection(int vectorSize) {
        try {
            boolean exists = qdrantClient
                    .collectionExistsAsync(properties.getCollectionName())
                    .get(5, TimeUnit.SECONDS);

            if (exists) {
                log.info("[SchemaIndex] Collection [{}] 已存在，跳过创建",
                        properties.getCollectionName());
                return;
            }

            qdrantClient.createCollectionAsync(
                    Collections.CreateCollection.newBuilder()
                            .setCollectionName(properties.getCollectionName())
                            .setVectorsConfig(Collections.VectorsConfig.newBuilder()
                                    .setParams(Collections.VectorParams.newBuilder()
                                            .setSize(vectorSize)
                                            .setDistance(Collections.Distance.Cosine)
                                            .build())
                                    .build())
                            .build()
            ).get(QDRANT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log.info("[SchemaIndex] Collection [{}] 创建成功，向量维度: {}",
                    properties.getCollectionName(), vectorSize);

        } catch (TimeoutException e) {
            throw new RuntimeException("Qdrant 连接超时，请检查服务是否可达", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("创建 Collection 被中断", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("创建 Collection 失败: " + cause(e), e);
        }
    }

    @Override
    public void upsertTables(Long dataSourceId, List<TableMetadata> tables) {
        if (tables == null || tables.isEmpty()) {
            log.warn("[SchemaIndex] 数据源 {} 无可索引的表，跳过", dataSourceId);
            return;
        }

        log.info("[SchemaIndex] 开始并行索引数据源 {}，共 {} 张表", dataSourceId, tables.size());

        List<List<TableMetadata>> batches = partition(tables);
        AtomicInteger successCount = new AtomicInteger(0);

        List<CompletableFuture<Void>> futures = batches.stream()
                .map(batch -> CompletableFuture.runAsync(() -> {
                    try {
                        List<Points.PointStruct> points = vectorizeBatch(dataSourceId, batch);
                        if (!points.isEmpty()) {
                            qdrantClient.upsertAsync(properties.getCollectionName(), points)
                                    .get(QDRANT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                            successCount.addAndGet(points.size());
                        }
                    } catch (Exception e) {
                        log.error("[SchemaIndex] 批次处理失败: {}", e.getMessage());
                    }
                }, indexExecutor))
                .toList();

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(5, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("[SchemaIndex] 索引任务等待超时或中断", e);
        }

        log.info("[SchemaIndex] 数据源 {} 索引任务结束：{}/{} 张表成功",
                dataSourceId, successCount.get(), tables.size());
    }

    @Override
    public void deleteByDataSource(Long dataSourceId) {
        try {
            qdrantClient.deleteAsync(
                    properties.getCollectionName(),
                    buildDataSourceFilter(dataSourceId)
            ).get(QDRANT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log.info("[SchemaIndex] 数据源 {} 的向量索引已全部删除", dataSourceId);

        } catch (TimeoutException e) {
            log.error("[SchemaIndex] 删除数据源 {} 向量索引超时，" +
                      "残留数据将被相似度阈值过滤", dataSourceId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[SchemaIndex] 删除向量索引被中断，dataSourceId: {}", dataSourceId);
        } catch (ExecutionException e) {
            log.error("[SchemaIndex] 删除数据源 {} 向量索引失败: {}",
                    dataSourceId, cause(e));
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("[SchemaIndex] 正在关闭索引线程池...");
        indexExecutor.shutdown();
    }
    // ==================== 私有方法 ====================

    /**
     * 将一批表元数据向量化，单张失败跳过，不影响整批
     */
    private List<Points.PointStruct> vectorizeBatch(Long dataSourceId,
                                                     List<TableMetadata> batch) {
        List<Points.PointStruct> points = new ArrayList<>(batch.size());

        for (TableMetadata table : batch) {
            try {
                String text   = buildSemanticText(table);
                List<Float> v = embeddingService.getVector(text);

                points.add(Points.PointStruct.newBuilder()
                        .setId(id(deterministicUuid(dataSourceId, table.getTableName())))
                        .setVectors(vectors(v))
                        .putPayload("table_name",    value(table.getTableName()))
                        .putPayload("data_source_id", value(dataSourceId))
                        .putPayload("table_comment",
                                value(table.getTableComment() != null
                                        ? table.getTableComment() : ""))
                        .build());

                log.debug("[SchemaIndex] 表 [{}] 向量化完成", table.getTableName());

            } catch (Exception e) {
                log.error("[SchemaIndex] 表 [{}] 向量化失败，已跳过: {}",
                        table.getTableName(), e.getMessage());
            }
        }
        return points;
    }

    /**
     * 构建表的语义描述文本。
     *
     * <p>格式：「表名 [表注释] 字段名 [字段注释] …」
     * 空白注释和占位注释不进入文本，减少向量空间噪音。
     */
    private String buildSemanticText(TableMetadata table) {
        StringBuilder sb = new StringBuilder(table.getTableName());

        if (meaningful(table.getTableComment())) {
            sb.append(' ').append(table.getTableComment());
        }

        if (table.getColumns() != null) {
            for (ColumnMetadata col : table.getColumns()) {
                sb.append(' ').append(col.getName());
                if (meaningful(col.getComment())
                        && !PLACEHOLDER_COMMENT.equals(col.getComment())) {
                    sb.append(' ').append(col.getComment());
                }
            }
        }
        return sb.toString();
    }

    /**
     * 构建按 data_source_id 过滤的 Qdrant Filter（供 delete 使用）
     */
    private Points.Filter buildDataSourceFilter(Long dataSourceId) {
        return Points.Filter.newBuilder()
                .addMust(Points.Condition.newBuilder()
                        .setField(Points.FieldCondition.newBuilder()
                                .setKey("data_source_id")
                                .setMatch(Points.Match.newBuilder()
                                        .setInteger(dataSourceId)
                                        .build()))
                        .build())
                .build();
    }

    /**
     * 生成确定性 UUID（UUID v3 语义）。
     * 同一 dataSourceId + tableName 永远生成相同 UUID，保证 upsert 幂等。
     */
    private UUID deterministicUuid(Long dataSourceId, String tableName) {
        String key = dataSourceId + ":" + tableName;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private boolean meaningful(String s) {
        return s != null && !s.isBlank();
    }

    private String cause(ExecutionException e) {
        return e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
    }

    private <T> List<List<T>> partition(List<T> list) {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += SchemaIndexingServiceImpl.BATCH_SIZE) {
            result.add(list.subList(i, Math.min(i + SchemaIndexingServiceImpl.BATCH_SIZE, list.size())));
        }
        return result;
    }
}