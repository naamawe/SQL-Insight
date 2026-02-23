package com.xhx.core.service.sql.Impl;

import com.xhx.core.extractor.MetadataExtractorRouter;
import com.xhx.core.model.TableMetadata;
import com.xhx.core.service.cache.CacheService;
import com.xhx.core.service.sql.SchemaCollectorService;
import com.xhx.dal.config.DynamicDataSourceManager;
import com.xhx.dal.entity.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Schema 采集服务实现
 * <p>
 * 缓存策略：
 *   缓存结构化的 List<TableMetadata>（JSON 序列化存 Redis）
 *   格式化（format）在 SchemaLinker 过滤之后执行，不进缓存
 * <p>
 * 多数据库支持：
 *   通过 MetadataExtractorRouter 按 dbType 路由到对应提取器
 *   各数据库注释获取方式差异由各自的 Extractor 实现封装
 *
 * @author master
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaCollectorServiceImpl implements SchemaCollectorService {

    private final DynamicDataSourceManager dataSourceManager;
    private final MetadataExtractorRouter metadataExtractorRouter;
    private final CacheService cacheService;

    @Override
    public List<TableMetadata> getMetadata(DataSource dsConfig, List<String> allowedTables) {
        if (CollectionUtils.isEmpty(allowedTables)) {
            return Collections.emptyList();
        }

        // 排序后生成 hash，相同权限组合命中同一份缓存
        List<String> sortedTables = allowedTables.stream().sorted().toList();
        String permHash = Integer.toHexString(sortedTables.hashCode());

        // 查缓存
        List<TableMetadata> cached = cacheService.getSchemaMetadata(dsConfig.getId(), permHash);
        if (cached != null) {
            log.debug("Schema 元数据缓存命中，数据源: {}, hash: {}", dsConfig.getId(), permHash);
            return cached;
        }

        // 缓存未命中，查目标库
        log.info("Schema 元数据缓存未命中，从目标库加载，数据源: {} [{}], 表数量: {}",
                dsConfig.getConnName(), dsConfig.getDbType(), sortedTables.size());

        javax.sql.DataSource dataSource = dataSourceManager.getDataSource(dsConfig);
        try (Connection conn = dataSource.getConnection()) {
            // 通过 Router 按 dbType 路由到对应的 Extractor
            List<TableMetadata> metadata = metadataExtractorRouter.extract(
                    dsConfig.getDbType(), conn, sortedTables);

            // 回填缓存
            cacheService.putSchemaMetadata(dsConfig.getId(), permHash, metadata);
            return metadata;
        } catch (SQLException e) {
            log.error("获取数据源 {} 的连接失败", dsConfig.getConnName(), e);
            throw new RuntimeException("数据库连接失败，请检查配置信息: " + e.getMessage());
        }
    }

    @Override
    public String format(List<TableMetadata> tables) {
        if (CollectionUtils.isEmpty(tables)) {
            return "抱歉，您当前没有访问该数据源下任何表的权限。";
        }
        return "以下是当前数据库的可操作表结构信息：\n\n" +
                tables.stream()
                        .map(TableMetadata::toPromptString)
                        .collect(Collectors.joining("\n---\n"));
    }
}