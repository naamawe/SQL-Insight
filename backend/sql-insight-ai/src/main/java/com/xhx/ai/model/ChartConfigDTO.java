package com.xhx.ai.model;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("xAxis")
    @JSONField(name = "xAxis", alternateNames = {"x_axis", "xaxis"})
    private String xAxis;

    /** Y 轴字段名列表 */
    @JsonProperty("yAxis")
    @JSONField(name = "yAxis", alternateNames = {"y_axis", "yaxis"})
    private List<String> yAxis;

    /** 图表标题 */
    private String title;
}