package com.xhx.core.extractor.Impl;

import com.xhx.core.extractor.MetadataExtractor;
import com.xhx.core.model.ColumnMetadata;
import com.xhx.core.model.TableMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.*;

/**
 * SQL Server 元数据提取器
 * <p>
 * 与 MySQL 的主要差异：
 *   1. schema 默认 "dbo"，catalog 用 conn.getCatalog()
 *   2. 表注释和字段注释存在 sys.extended_properties 里，property_name = 'MS_Description'
 *   3. 索引通过 sys.indexes + sys.index_columns 查询
 *
 * @author master
 */
@Slf4j
@Component
public class SqlServerMetadataExtractor implements MetadataExtractor {

    private static final String DEFAULT_SCHEMA = "dbo";

    @Override
    public boolean supports(String dbType) {
        return "sqlserver".equals(dbType);
    }

    @Override
    public List<TableMetadata> extract(Connection conn, List<String> tableNames) {
        List<TableMetadata> tables = new ArrayList<>();
        try {
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = conn.getCatalog();

            Map<String, String> tableComments = fetchTableComments(conn, tableNames);
            Map<String, Map<String, String>> columnComments =
                    fetchColumnComments(conn, tableNames);

            for (String tableName : tableNames) {
                TableMetadata table = new TableMetadata();
                table.setTableName(tableName);
                table.setTableComment(tableComments.get(tableName));

                // 提取主键
                Set<String> primaryKeySet = new HashSet<>();
                try (ResultSet rs = metaData.getPrimaryKeys(catalog, DEFAULT_SCHEMA, tableName)) {
                    while (rs.next()) {
                        primaryKeySet.add(rs.getString("COLUMN_NAME"));
                    }
                }

                // 提取索引
                Set<String> indexSet = new HashSet<>();
                try (ResultSet rs = metaData.getIndexInfo(catalog, DEFAULT_SCHEMA, tableName,
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
                Map<String, String> colCommentMap =
                        columnComments.getOrDefault(tableName, Collections.emptyMap());

                try (ResultSet rs = metaData.getColumns(catalog, DEFAULT_SCHEMA, tableName, null)) {
                    while (rs.next()) {
                        String colName = rs.getString("COLUMN_NAME");
                        cols.add(ColumnMetadata.builder()
                                .name(colName)
                                .type(rs.getString("TYPE_NAME"))
                                .comment(colCommentMap.get(colName))
                                .primaryKey(primaryKeySet.contains(colName))
                                .indexed(indexSet.contains(colName))
                                .build());
                    }
                }

                table.setColumns(cols);
                tables.add(table);
            }
        } catch (SQLException e) {
            log.error("SQL Server 元数据提取失败，表: {}", tableNames, e);
            throw new RuntimeException("SQL Server 元数据提取异常", e);
        }
        return tables;
    }

    /**
     * 查询表注释（sys.extended_properties，class=1 表示表，minor_id=0）
     */
    private Map<String, String> fetchTableComments(Connection conn,
                                                    List<String> tableNames) throws SQLException {
        Map<String, String> result = new HashMap<>();
        if (tableNames.isEmpty()) {
            return result;
        }

        String placeholders = String.join(",",
                Collections.nCopies(tableNames.size(), "?"));
        String sql = """
                SELECT t.name AS table_name,
                       ep.value AS comment
                FROM sys.tables t
                LEFT JOIN sys.extended_properties ep
                    ON ep.major_id = t.object_id
                   AND ep.minor_id = 0
                   AND ep.class = 1
                   AND ep.name = 'MS_Description'
                WHERE SCHEMA_NAME(t.schema_id) = 'dbo'
                  AND t.name IN (%s)
                """.formatted(placeholders);

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < tableNames.size(); i++) {
                ps.setString(i + 1, tableNames.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("table_name"), rs.getString("comment"));
                }
            }
        }
        return result;
    }

    /**
     * 查询字段注释（sys.extended_properties，class=1，minor_id=字段序号）
     */
    private Map<String, Map<String, String>> fetchColumnComments(
            Connection conn, List<String> tableNames) throws SQLException {

        Map<String, Map<String, String>> result = new HashMap<>();
        if (tableNames.isEmpty()) {
            return result;
        }

        String placeholders = String.join(",",
                Collections.nCopies(tableNames.size(), "?"));
        String sql = """
                SELECT t.name AS table_name,
                       c.name AS column_name,
                       ep.value AS comment
                FROM sys.tables t
                JOIN sys.columns c ON c.object_id = t.object_id
                LEFT JOIN sys.extended_properties ep
                    ON ep.major_id = c.object_id
                   AND ep.minor_id = c.column_id
                   AND ep.class = 1
                   AND ep.name = 'MS_Description'
                WHERE SCHEMA_NAME(t.schema_id) = 'dbo'
                  AND t.name IN (%s)
                """.formatted(placeholders);

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < tableNames.size(); i++) {
                ps.setString(i + 1, tableNames.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("table_name");
                    String columnName = rs.getString("column_name");
                    String comment = rs.getString("comment");
                    result.computeIfAbsent(tableName, k -> new HashMap<>())
                            .put(columnName, comment);
                }
            }
        }
        return result;
    }
}