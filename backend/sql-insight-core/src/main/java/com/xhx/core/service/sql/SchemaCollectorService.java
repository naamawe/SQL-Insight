package com.xhx.core.service.sql;

import com.xhx.common.model.TableMetadata;
import com.xhx.dal.entity.DataSource;

import java.util.List;

/**
 * Schema 采集服务
 * @author master
 */
public interface SchemaCollectorService {

    /**
     * 获取指定表的结构化元数据列表（带 Redis 缓存）
     * SchemaLinker 需要用结构化数据做关键词评分，所以单独暴露此方法
     *
     * @param dsConfig     数据源配置
     * @param allowedTables 当前用户有权限的表名列表
     * @return 结构化元数据列表
     */
    List<TableMetadata> getMetadata(DataSource dsConfig, List<String> allowedTables);

    /**
     * 将元数据列表格式化为 AI Prompt 所需的 Markdown 文本
     *
     * @param tables 经过 SchemaLinker 过滤后的表元数据
     * @return schema markdown 字符串
     */
    String format(List<TableMetadata> tables);
}