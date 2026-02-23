package com.xhx.core.extractor;

import com.xhx.core.model.TableMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.List;

/**
 * 元数据提取器路由
 * 根据 dbType 选择对应的提取器实现，对外屏蔽各数据库差异
 * @author master
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetadataExtractorRouter {

    /** Spring 自动注入所有 MetadataExtractor 实现 */
    private final List<MetadataExtractor> extractors;

    /**
     * 根据数据库类型路由到对应提取器并执行元数据提取
     *
     * @param dbType     数据库类型（mysql / postgresql / sqlserver）
     * @param conn       目标数据库连接
     * @param tableNames 需要提取的表名列表
     * @return 结构化元数据列表
     */
    public List<TableMetadata> extract(String dbType, Connection conn, List<String> tableNames) {
        MetadataExtractor extractor = extractors.stream()
                .filter(e -> e.supports(dbType.toLowerCase()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "不支持的数据库类型，没有对应的元数据提取器: " + dbType));

        log.debug("使用提取器: {} 处理数据库类型: {}",
                extractor.getClass().getSimpleName(), dbType);
        return extractor.extract(conn, tableNames);
    }
}