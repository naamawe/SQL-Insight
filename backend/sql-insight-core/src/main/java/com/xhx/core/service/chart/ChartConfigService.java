package com.xhx.core.service.chart;

import com.xhx.dal.entity.ChartConfig;

import java.util.Optional;

/**
 * 图表配置服务
 *
 * @author master
 */
public interface ChartConfigService {

    /**
     * 保存或更新图表配置
     *
     * @param config 图表配置
     */
    void saveOrUpdate(ChartConfig config);

    /**
     * 根据记录 ID 获取图表配置
     *
     * @param recordId 记录 ID
     * @return 图表配置，不存在时返回 Optional.empty()
     */
    Optional<ChartConfig> getByRecordId(Long recordId);

    /**
     * 根据记录 ID 删除图表配置
     *
     * @param recordId 记录 ID
     */
    void deleteByRecordId(Long recordId);
}