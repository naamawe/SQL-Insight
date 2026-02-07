package com.xhx.core.service.Impl;

import com.xhx.core.extractor.MetadataExtractor;
import com.xhx.core.model.TableMetadata;
import com.xhx.core.service.SchemaCollectorService;
import com.xhx.dal.config.DynamicDataSourceManager;
import com.xhx.dal.entity.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 元数据采集服务实现类
 * @author master
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaCollectorServiceImpl implements SchemaCollectorService {

    // 通过构造函数注入
    private final DynamicDataSourceManager dataSourceManager;
    private final MetadataExtractor metadataExtractor;

    @Override
    public String fetchPublicSchema(DataSource dsConfig, List<String> allowedTables) {
        if (CollectionUtils.isEmpty(allowedTables)) {
            log.warn("数据源 {} 没有被允许访问的表", dsConfig.getConnName());
            return "抱歉，您当前没有访问该数据源下任何表的权限。";
        }

        javax.sql.DataSource dataSource = dataSourceManager.getDataSource(dsConfig);
        try (Connection conn = dataSource.getConnection()) {

            List<TableMetadata> tableMetas = metadataExtractor.extract(conn, allowedTables);
            return formatSchema(tableMetas);

        } catch (SQLException e) {
            log.error("获取数据源 {} 的连接失败", dsConfig.getConnName(), e);
            throw new RuntimeException("数据库连接失败，请检查配置信息: " + e.getMessage());
        }
    }

    /**
     * 将元数据列表转换为 AI 易于理解的 Markdown 文本
     */
    private String formatSchema(List<TableMetadata> tables) {
        return "以下是当前数据库的可操作表结构信息：\n\n" +
                tables.stream()
                        .map(TableMetadata::toPromptString)
                        .collect(Collectors.joining("\n---\n"));
    }
}
