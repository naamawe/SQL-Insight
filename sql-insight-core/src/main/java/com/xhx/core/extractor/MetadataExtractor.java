package com.xhx.core.extractor;

import com.xhx.core.model.TableMetadata;

import java.sql.Connection;
import java.util.List;

/**
 * @author master
 */
public interface MetadataExtractor {

    /**
     * 获取元数据
     * @param conn 数据库连接
     * @param tableNames 表名
     * @return 元数据
     */
    List<TableMetadata> extract(Connection conn, List<String> tableNames);
}
