package com.xhx.core.extractor;

import com.xhx.core.model.TableMetadata;

import java.sql.Connection;
import java.util.List;

/**
 * 数据库元数据提取器接口
 * @author master
 */
public interface MetadataExtractor {

    /**
     * 判断当前提取器是否支持指定数据库类型
     * @param dbType 数据库类型（mysql / postgresql / oracle / sqlserver）
     */
    boolean supports(String dbType);

    /**
     * 提取指定表的结构化元数据
     * @param conn       目标数据库连接
     * @param tableNames 需要提取的表名列表
     * @return 结构化元数据列表
     */
    List<TableMetadata> extract(Connection conn, List<String> tableNames);
}