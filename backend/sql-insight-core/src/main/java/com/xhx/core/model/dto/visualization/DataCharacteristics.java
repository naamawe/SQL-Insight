package com.xhx.core.model.dto.visualization;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 数据特征分析结果
 * 用于辅助前端进行可视化决策
 * @author master
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DataCharacteristics {
    /**
     * 是否包含时间列
     */
    private Boolean hasTimeColumn;

    /**
     * 是否包含数值列
     */
    private Boolean hasNumericColumn;

    /**
     * 是否为聚合查询结果
     */
    private Boolean isAggregation;

    /**
     * 是否为单个值
     */
    private Boolean isSingleValue;

    /**
     * 行数
     */
    private Integer rowCount;

    /**
     * 列数
     */
    private Integer columnCount;

    /**
     * 数值列列表
     */
    private List<String> numericColumns;

    /**
     * 分类列列表
     */
    private List<String> categoricalColumns;

    /**
     * 时间列名称
     */
    private String timeColumn;
}
