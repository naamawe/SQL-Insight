package com.xhx.core.model.dto;

import lombok.Data;

/**
     * 图表配置请求体
     */
@Data
public class ChartConfigDTO {
    private String type;
    private String xAxis;
    private java.util.List<String> yAxis;
    private String title;
}