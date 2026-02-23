package com.xhx.core.extractor.Impl;

import com.xhx.core.extractor.MetadataExtractor;
import com.xhx.common.model.ColumnMetadata;
import com.xhx.common.model.TableMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * MySQL 元数据提取器
 * MySQL 的表注释和字段注释通过标准 JDBC REMARKS 可以直接获取
 * 需要在 JDBC URL 中加上 useInformationSchema=true&remarks=true 才能拿到注释
 * @author master
 */
@Slf4j
@Component
public class MySqlMetadataExtractor implements MetadataExtractor {

    @Override
    public boolean supports(String dbType) {
        return "mysql".equals(dbType);
    }

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
                try (ResultSet rs = metaData.getTables(catalog, null, tableName,
                        new String[]{"TABLE"})) {
                    if (rs.next()) {
                        table.setTableComment(rs.getString("REMARKS"));
                    }
                }

                // 提取主键
                Set<String> primaryKeySet = new HashSet<>();
                try (ResultSet rs = metaData.getPrimaryKeys(catalog, null, tableName)) {
                    while (rs.next()) {
                        primaryKeySet.add(rs.getString("COLUMN_NAME"));
                    }
                }

                // 提取索引
                Set<String> indexSet = new HashSet<>();
                try (ResultSet rs = metaData.getIndexInfo(catalog, null, tableName,
                        false, false)) {
                    while (rs.next()) {
                        String colName = rs.getString("COLUMN_NAME");
                        if (colName != null) {
                            indexSet.add(colName);
                        }
                    }
                }

                // 提取字段
                List<ColumnMetadata> cols = new ArrayList<>();
                try (ResultSet rs = metaData.getColumns(catalog, null, tableName, null)) {
                    while (rs.next()) {
                        String colName = rs.getString("COLUMN_NAME");
                        cols.add(ColumnMetadata.builder()
                                .name(colName)
                                .type(rs.getString("TYPE_NAME"))
                                .comment(rs.getString("REMARKS"))
                                .primaryKey(primaryKeySet.contains(colName))
                                .indexed(indexSet.contains(colName))
                                .build());
                    }
                }

                table.setColumns(cols);
                tables.add(table);
            }
        } catch (SQLException e) {
            log.error("MySQL 元数据提取失败，表: {}", tableNames, e);
            throw new RuntimeException("MySQL 元数据提取异常", e);
        }
        return tables;
    }
}