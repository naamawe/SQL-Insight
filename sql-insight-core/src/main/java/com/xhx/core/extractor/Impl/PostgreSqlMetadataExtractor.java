package com.xhx.core.extractor.Impl;

import com.xhx.core.extractor.MetadataExtractor;
import com.xhx.common.model.ColumnMetadata;
import com.xhx.common.model.TableMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.*;

/**
 * PostgreSQL 元数据提取器
 * <p>
 * 与 MySQL 的主要差异：
 *   1. catalog 传 null，schema 用 "public"
 *   2. 表注释和字段注释不走标准 JDBC REMARKS，需要查 pg_description 系统表
 *   3. JDBC 驱动默认不填充 REMARKS，查系统表是更可靠的方式
 *
 * @author master
 */
@Slf4j
@Component
public class PostgreSqlMetadataExtractor implements MetadataExtractor {

    private static final String DEFAULT_SCHEMA = "public";

    @Override
    public boolean supports(String dbType) {
        return "postgresql".equals(dbType);
    }

    @Override
    public List<TableMetadata> extract(Connection conn, List<String> tableNames) {
        List<TableMetadata> tables = new ArrayList<>();
        try {
            DatabaseMetaData metaData = conn.getMetaData();

            // 批量查询表注释和字段注释
            Map<String, String> tableComments = fetchTableComments(conn, tableNames);
            Map<String, Map<String, String>> columnComments =
                    fetchColumnComments(conn, tableNames);

            for (String tableName : tableNames) {
                TableMetadata table = new TableMetadata();
                table.setTableName(tableName);
                table.setTableComment(tableComments.get(tableName));

                // 提取主键
                Set<String> primaryKeySet = new HashSet<>();
                try (ResultSet rs = metaData.getPrimaryKeys(null, DEFAULT_SCHEMA, tableName)) {
                    while (rs.next()) {
                        primaryKeySet.add(rs.getString("COLUMN_NAME"));
                    }
                }

                // 提取索引
                Set<String> indexSet = new HashSet<>();
                try (ResultSet rs = metaData.getIndexInfo(null, DEFAULT_SCHEMA, tableName,
                        false, false)) {
                    while (rs.next()) {
                        String colName = rs.getString("COLUMN_NAME");
                        if (colName != null) {
                            indexSet.add(colName);
                        }
                    }
                }

                // 提取字段，注释从 pg_description 的查询结果里取
                List<ColumnMetadata> cols = new ArrayList<>();
                Map<String, String> colCommentMap =
                        columnComments.getOrDefault(tableName, Collections.emptyMap());

                try (ResultSet rs = metaData.getColumns(null, DEFAULT_SCHEMA, tableName, null)) {
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
            log.error("PostgreSQL 元数据提取失败，表: {}", tableNames, e);
            throw new RuntimeException("PostgreSQL 元数据提取异常", e);
        }
        return tables;
    }

    /**
     * 批量查询表注释
     * pg_description + pg_class 联查
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
                SELECT c.relname AS table_name,
                       d.description AS comment
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                LEFT JOIN pg_description d ON d.objoid = c.oid AND d.objsubid = 0
                WHERE n.nspname = 'public'
                  AND c.relkind = 'r'
                  AND c.relname IN (%s)
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
     * 批量查询字段注释
     * pg_description + pg_class + pg_attribute 联查
     */
    private Map<String, Map<String, String>> fetchColumnComments(
            Connection conn, List<String> tableNames) throws SQLException {

        // 结构：Map<tableName, Map<columnName, comment>>
        Map<String, Map<String, String>> result = new HashMap<>();
        if (tableNames.isEmpty()) {
            return result;
        }

        String placeholders = String.join(",",
                Collections.nCopies(tableNames.size(), "?"));
        String sql = """
                SELECT c.relname AS table_name,
                       a.attname AS column_name,
                       d.description AS comment
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum > 0
                LEFT JOIN pg_description d ON d.objoid = c.oid AND d.objsubid = a.attnum
                WHERE n.nspname = 'public'
                  AND c.relkind = 'r'
                  AND c.relname IN (%s)
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