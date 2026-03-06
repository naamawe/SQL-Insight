package com.xhx.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 图表配置 DTO
 *
 * @author master
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChartConfigDTO {

    /** 图表类型：bar/line/pie/scatter/table */
    private String type;

    /** X 轴字段名 */
    private String xAxis;

    /** Y 轴字段名列表 */
    private List<String> yAxis;

    /** 图表标题 */
    private String title;
}