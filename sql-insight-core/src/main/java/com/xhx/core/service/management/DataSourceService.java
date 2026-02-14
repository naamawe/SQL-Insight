package com.xhx.core.service.management;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhx.dal.entity.DataSource;

import java.util.List;

/**
 * @author master
 */
public interface DataSourceService {

    /**
     * 测试数据源连接
     * @param ds 数据源
     */
    void testConnection(DataSource ds);

    /**
     * 添加数据源
     * @param ds 数据源
     */
    void addDataSource(DataSource ds);

    /**
     * 获取数据源下的所有表名
     * @param id 数据源 id
     * @return 表名列表
     */
    List<String> getTableNames(Long id);

    /**
     * 获取数据源分页列表
     * @param current 页码
     * @param size 每页数量
     * @param connName 数据源名称
     * @return 数据源分页列表
     */
    Page<DataSource> getDataSourcePage(int current, int size, String connName);

    /**
     * 修改数据源
     * @param ds 数据源
     */
    void updateDataSource(DataSource ds);

    /**
     * 删除数据源
     * @param id 数据源 id
     */
    void deleteDataSource(Long id);
}
