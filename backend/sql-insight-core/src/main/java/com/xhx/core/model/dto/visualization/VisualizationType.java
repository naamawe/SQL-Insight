package com.xhx.core.model.dto.visualization;

/**
 * 可视化类型枚举
 * @author master
 */
public enum VisualizationType {
    /**
     * 大数字指标卡 - 单个统计值
     * 适用场景: 单行单列,聚合函数结果
     * 示例: "总销售额是多少?" → SELECT SUM(amount) as total
     */
    METRIC,

    /**
     * 卡片列表 - 少量实体记录
     * 适用场景: ≤10行, 2-5列, 非聚合查询
     * 示例: "前5名销售员" → SELECT name, sales FROM ... LIMIT 5
     */
    CARDS,

    /**
     * 表格 - 复杂数据/大量记录
     * 适用场景: >10行 或 >5列, 明细数据
     * 示例: "所有订单详情" → SELECT * FROM orders
     */
    TABLE,

    /**
     * 柱状图 - 分类对比
     * 适用场景: GROUP BY + 聚合, 分类维度 + 数值
     * 示例: "各部门销售额" → SELECT dept, SUM(sales) GROUP BY dept
     */
    BAR_CHART,

    /**
     * 折线图 - 时间趋势
     * 适用场景: 时间字段 + 数值, ORDER BY 时间
     * 示例: "每月销售趋势" → SELECT month, SUM(sales) GROUP BY month
     */
    LINE_CHART,

    /**
     * 饼图 - 占比分析
     * 适用场景: 分类 + 聚合, 适合展示百分比
     * 示例: "各产品销售占比" → SELECT product, COUNT(*) GROUP BY product
     */
    PIE_CHART,

    /**
     * 面积图 - 趋势累积
     * 适用场景: 时间序列 + 累积数据
     */
    AREA_CHART,

    /**
     * 散点图 - 相关性分析
     * 适用场景: 两个数值维度的关系
     */
    SCATTER_CHART,

    /**
     * 热力图 - 矩阵数据
     * 适用场景: 二维分类 + 数值
     */
    HEATMAP,

    /**
     * 时间线 - 事件序列
     * 适用场景: 时间 + 事件描述
     */
    TIMELINE,

    /**
     * 对比视图 - 多维对比
     * 适用场景: 多个指标的对比展示
     */
    COMPARISON
}
