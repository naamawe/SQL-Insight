package com.xhx.core.service.sql.Impl;

import com.xhx.common.exception.ServiceException;
import com.xhx.common.model.TableMetadata;
import com.xhx.core.extractor.MetadataExtractorRouter;
import com.xhx.core.service.cache.CacheService;
import com.xhx.core.service.management.DataSourcePasswordCipher;
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
 * @author master
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaCollectorServiceImpl implements SchemaCollectorService {

    private final DynamicDataSourceManager dataSourceManager;
    private final MetadataExtractorRouter metadataExtractorRouter;
    private final CacheService cacheService;
    private final DataSourcePasswordCipher passwordCipher;

    @Override
    public List<TableMetadata> getMetadata(DataSource dsConfig, List<String> allowedTables) {
        if (CollectionUtils.isEmpty(allowedTables)) {
            return Collections.emptyList();
        }

        List<String> sortedTables = allowedTables.stream().sorted().toList();
        String permHash = Integer.toHexString(sortedTables.hashCode());

        List<TableMetadata> cached = cacheService.getSchemaMetadata(dsConfig.getId(), permHash);
        if (cached != null) {
            log.debug("Schema 元数据缓存命中，数据源: {}, hash: {}", dsConfig.getId(), permHash);
            return cached;
        }

        log.info("Schema 元数据缓存未命中，从目标库加载，数据源: {} [{}], 表数量: {}",
                dsConfig.getConnName(), dsConfig.getDbType(), sortedTables.size());

        javax.sql.DataSource dataSource = dataSourceManager.getDataSource(
                passwordCipher.decryptedCopy(dsConfig));
        try (Connection conn = dataSource.getConnection()) {
            List<TableMetadata> metadata = metadataExtractorRouter.extract(
                    dsConfig.getDbType(), conn, sortedTables);
            cacheService.putSchemaMetadata(dsConfig.getId(), permHash, metadata);
            return metadata;
        } catch (SQLException e) {
            log.error("获取数据源 {} 的连接失败", dsConfig.getConnName(), e);
            throw new ServiceException("数据库连接失败，请检查配置信息: " + e.getMessage());
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