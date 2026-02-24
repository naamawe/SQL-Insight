package com.xhx.ai.service;

import com.xhx.ai.config.QdrantProperties;
import com.xhx.common.model.TableMetadata;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * 向量语义 Schema Linker（@Primary 主实现）
 *
 * @author master
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class VectorSchemaLinker implements SchemaLinker {

    private final QdrantClient            qdrantClient;
    private final AliyunEmbeddingService  embeddingService;
    private final QdrantProperties        properties;
    private final KeywordSchemaLinker     keywordFallback;

    private static final float SIMILARITY_THRESHOLD  = 0.55f;
    private static final int   SEARCH_LIMIT          = 10;
    private static final long  TIMEOUT_SECONDS       = 3L;

    @Override
    public List<TableMetadata> link(String question, Long dataSourceId,
                                    List<TableMetadata> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        try {
            return doVectorLink(question, dataSourceId, candidates);
        } catch (TimeoutException e) {
            log.warn("[VectorLinker] Qdrant 检索超时 ({}s)，降级关键词匹配", TIMEOUT_SECONDS);
            return keywordFallback.link(question, dataSourceId, candidates);
        } catch (Exception e) {
            log.error("[VectorLinker] 向量检索异常，降级关键词匹配: {}", e.getMessage());
            return keywordFallback.link(question, dataSourceId, candidates);
        }
    }

    // ==================== 核心检索流程 ====================

    private List<TableMetadata> doVectorLink(String question, Long dataSourceId,
                                             List<TableMetadata> candidates) throws Exception {
        Map<String, TableMetadata> candidateMap = candidates.stream()
                .collect(Collectors.toMap(TableMetadata::getTableName, c -> c, (v1, v2) -> v1));

        // 用户问题向量化
        List<Float> queryVector = embeddingService.getVector(question);

        // 向量检索
        List<Points.ScoredPoint> hits = qdrantClient
                .searchAsync(buildSearchRequest(queryVector, dataSourceId))
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        log.debug("[VectorLinker] Qdrant 返回 {} 个命中点，dsId: {}", hits.size(), dataSourceId);

        // 向量无命中 → 降级关键词匹配
        if (hits.isEmpty()) {
            log.info("[VectorLinker] 向量检索无结果（阈值 {}），降级关键词匹配", SIMILARITY_THRESHOLD);
            return keywordFallback.link(question, dataSourceId, candidates);
        }

        List<TableMetadata> result = hits.stream()
                .map(hit -> hit.getPayloadMap().get("table_name").getStringValue())
                .map(candidateMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 交集为空（全被权限过滤）→ 降级关键词匹配
        if (result.isEmpty()) {
            log.warn("[VectorLinker] 向量命中的表均不在用户权限范围内，降级关键词匹配");
            return keywordFallback.link(question, dataSourceId, candidates);
        }

        log.info("[VectorLinker] 完成：候选 {} 张 → 向量命中 {} 张 → 最终输出 {} 张",
                candidates.size(), hits.size(), result.size());

        return result;
    }
    /**
     * 构建 Qdrant 检索请求
     *
     * <p>Must Filter 按 data_source_id 过滤，确保不同数据源之间向量完全隔离（修复①）。
     */
    private Points.SearchPoints buildSearchRequest(List<Float> queryVector, Long dataSourceId) {
        Points.Filter dataSourceFilter = Points.Filter.newBuilder()
                .addMust(Points.Condition.newBuilder()
                        .setField(Points.FieldCondition.newBuilder()
                                .setKey("data_source_id")
                                .setMatch(Points.Match.newBuilder()
                                        .setInteger(dataSourceId)
                                        .build()))
                        .build())
                .build();

        return Points.SearchPoints.newBuilder()
                .setCollectionName(properties.getCollectionName())
                .addAllVector(queryVector)
                .setFilter(dataSourceFilter)
                .setWithPayload(Points.WithPayloadSelector.newBuilder()
                        .setEnable(true)
                        .build())
                .setLimit(SEARCH_LIMIT)
                .setScoreThreshold(SIMILARITY_THRESHOLD)
                .build();
    }
}