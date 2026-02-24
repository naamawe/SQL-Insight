package com.xhx.core.service.management;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhx.core.model.dto.DataSourceSaveDTO;
import com.xhx.core.model.dto.DataSourceUpdateDTO;
import com.xhx.core.model.vo.DataSourceVO;

import java.util.List;

/**
 * @author master
 */
public interface DataSourceService {

    /**
     * 测试数据源连接
     */
    void testConnection(DataSourceSaveDTO saveDto);

    /**
     * 添加数据源
     */
    void addDataSource(DataSourceSaveDTO saveDto);

    /**
     * 获取数据源下的所有表名
     */
    List<String> getTableNames(Long id);

    /**
     * 手动刷新表名缓存
     * @param id 数据源ID
     * @return 表名列表
     */
    List<String> refreshTableNames(Long id);

    /**
     * 分页查询数据源列表（管理员用）
     */
    Page<DataSourceVO> getDataSourcePage(int current, int size, String connName);

    /**
     * 获取所有数据源列表（不分页，用于下拉框）
     */
    List<DataSourceVO> getAllDataSources();

    /**
     * 根据ID获取单个数据源详情
     */
    DataSourceVO getDataSourceById(Long id);

    /**
     * 获取用户有权访问的数据源列表（普通用户用）
     */
    List<DataSourceVO> getMyDataSources(Long userId);

    /**
     * 修改数据源
     */
    void updateDataSource(DataSourceUpdateDTO updateDto);

    /**
     * 删除数据源
     */
    void deleteDataSource(Long id);

    /**
     * 批量删除数据源
     */
    void batchDeleteDataSources(List<Long> ids);
}