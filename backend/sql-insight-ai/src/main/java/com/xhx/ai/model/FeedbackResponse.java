package com.xhx.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 反馈响应（摘要 + 图表配置）
 *
 * @author master
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackResponse {

    /** 自然语言摘要 */
    private String summary;

    /** 图表配置推荐 */
    private ChartConfigDTO chart;
}