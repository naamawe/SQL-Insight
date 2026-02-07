package com.xhx.core.service;

import com.xhx.dal.entity.DataSource;

import java.util.List;

/**
 * @author master
 */

public interface SchemaCollectorService {

    /**
     * 根据数据源和权限表，抓取AI所需的元数据信息
     * @param ds 数据源
     * @param allowsTables 允许的表
     * @return 元数据信息
     */
    String fetchPublicSchema(DataSource ds, List<String> allowsTables);
}
