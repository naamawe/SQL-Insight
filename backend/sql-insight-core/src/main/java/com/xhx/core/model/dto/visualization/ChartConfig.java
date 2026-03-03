package com.xhx.core.model.dto.visualization;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 图表配置
 * @author master
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChartConfig {
    /**
     * X轴字段名
     */
    private String xAxis;

    /**
     * Y轴字段名
     */
    private String yAxis;

    /**
     * 系列字段名(用于多系列图表)
     */
    private String series;

    /**
     * 图表标题
     */
    private String title;

    /**
     * 其他配置选项(扩展用)
     */
    private Map<String, Object> options;
}
