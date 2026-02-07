package com.xhx.core.extractor.Impl;

import com.xhx.core.extractor.MetadataExtractor;
import com.xhx.core.model.ColumnMetadata;
import com.xhx.core.model.TableMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * MySQL元数据提取器
 * @author master
 */
@Slf4j
@Component
public class MySqlMetadataExtractor implements MetadataExtractor {

    @Override
    public List<TableMetadata> extract(Connection conn, List<String> tableNames) {
        List<TableMetadata> tables = new ArrayList<>();
        try {
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = conn.getCatalog();

            for (String tableName : tableNames) {
                TableMetadata table = new TableMetadata();
                table.setTableName(tableName);

                // 提取表注释
                try (ResultSet rs = metaData.getTables(catalog, null, tableName, new String[]{"TABLE"})) {
                    if (rs.next()) {
                        table.setTableComment(rs.getString("REMARKS"));
                    }
                }

                // 提取该表的主键字段集合
                Set<String> primaryKeySet = new HashSet<>();
                try (ResultSet rs = metaData.getPrimaryKeys(catalog, null, tableName)) {
                    while (rs.next()) {
                        primaryKeySet.add(rs.getString("COLUMN_NAME"));
                    }
                }

                // 提取该表的索引字段集合
                Set<String> indexSet = new HashSet<>();
                try (ResultSet rs = metaData.getIndexInfo(catalog, null, tableName, false, false)) {
                    while (rs.next()) {
                        String colName = rs.getString("COLUMN_NAME");
                        if (colName != null) {
                            indexSet.add(colName);
                        }
                    }
                }

                // 提取字段明细并组装模型
                List<ColumnMetadata> cols = new ArrayList<>();
                try (ResultSet rs = metaData.getColumns(catalog, null, tableName, null)) {
                    while (rs.next()) {
                        String colName = rs.getString("COLUMN_NAME");

                        ColumnMetadata column = ColumnMetadata.builder()
                                .name(colName)
                                .type(rs.getString("TYPE_NAME"))
                                .comment(rs.getString("REMARKS"))
                                .primaryKey(primaryKeySet.contains(colName))
                                .indexed(indexSet.contains(colName))
                                .build();

                        cols.add(column);
                    }
                }

                table.setColumns(cols);
                tables.add(table);
            }
        } catch (SQLException e) {
            log.error("提取表的元数据失败: {}", tableNames, e);
            throw new RuntimeException("数据库元数据提取异常", e);
        }
        return tables;
    }
}