package com.xhx.core.model.dto.visualization;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 可视化配置
 * @author master
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VisualizationConfig {
    /**
     * 主要渲染类型(AI推荐的最佳展示方式)
     */
    private VisualizationType primaryType;

    /**
     * 备选渲染类型(用户可切换)
     */
    private List<VisualizationType> alternativeTypes;

    /**
     * 图表配置(如果是图表类型)
     */
    private ChartConfig chartConfig;

    /**
     * 数据特征(辅助前端决策)
     */
    private DataCharacteristics characteristics;

    /**
     * AI推荐理由
     */
    private String reason;
}
