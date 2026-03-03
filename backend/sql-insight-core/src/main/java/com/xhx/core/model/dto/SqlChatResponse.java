package com.xhx.core.model.dto;

import com.xhx.core.model.dto.visualization.VisualizationConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * SQL 对话响应
 * @author master
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SqlChatResponse {
    /**
     * 会话ID
     */
    private Long sessionId;

    /**
     * 生成的SQL语句
     */
    private String sql;

    /**
     * 查询结果数据
     */
    private List<Map<String, Object>> data;

    /**
     * 结果总数
     */
    private Integer total;

    /**
     * AI生成的摘要
     */
    private String summary;

    /**
     * 可视化配置(可选,向后兼容)
     * 如果为null,前端将使用默认表格渲染
     */
    private VisualizationConfig visualization;
}