package com.xhx.ai.service;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points.SearchPoints;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.WithPayloadSelector;
import com.xhx.ai.config.QdrantProperties;
import com.xhx.common.model.TableMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author master
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class VectorSchemaLinker implements SchemaLinker {

    private final QdrantClient qdrantClient;
    private final AliyunEmbeddingService embeddingService;
    private final QdrantProperties properties;

    // 语义相似度阈值
    private static final float SIMILARITY_THRESHOLD = 0.55f;
    // Qdrant 检索返回的上限
    private static final int LIMIT = 10;

    @Override
    public List<TableMetadata> link(String question, List<TableMetadata> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return candidates;
        }

        try {
            // 1. 将用户问题向量化
            List<Float> queryVector = embeddingService.getVector(question);

            // 2. 从 Qdrant 检索语义最相关的表名
            SearchPoints searchPoints = SearchPoints.newBuilder()
                    .setCollectionName(properties.getCollectionName())
                    .addAllVector(queryVector)
                    .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build())
                    .setLimit(LIMIT)
                    .setScoreThreshold(SIMILARITY_THRESHOLD)
                    .build();

            // 阻塞获取结果（企业级生产建议设置 timeout）
            List<ScoredPoint> scoredPoints = qdrantClient.searchAsync(searchPoints).get();

            // 3. 提取检索出的表名
            Set<String> matchedTableNames = scoredPoints.stream()
                    .map(point -> point.getPayloadMap().get("table_name").getStringValue())
                    .collect(Collectors.toSet());

            // 4. 与权限列表做交集（只有在 candidates 里的表才允许被返回）
            List<TableMetadata> result = candidates.stream()
                    .filter(c -> matchedTableNames.contains(c.getTableName()))
                    .collect(Collectors.toList());

            // 5. 兜底策略：如果语义没找着，先返回原列表（或者你可以调用 KeywordSchemaLinker）
            if (result.isEmpty()) {
                log.warn("向量检索未匹配到高度相关的表，返回原始候选列表。相似度阈值: {}", SIMILARITY_THRESHOLD);
                return candidates;
            }

            log.info("向量 Schema Linking 完成：匹配到 {} 张相关表，耗时检索 {} 条", result.size(), scoredPoints.size());
            return result;

        } catch (Exception e) {
            log.error("向量检索执行异常，回退至全量模式: {}", e.getMessage());
            return candidates; 
        }
    }
}