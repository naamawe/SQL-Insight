// =============================================
// 可视化相关类型定义
// =============================================

/** 可视化类型枚举 */
export type VisualizationType =
  | 'METRIC'        // 大数字指标卡
  | 'CARDS'         // 卡片列表
  | 'TABLE'         // 表格
  | 'BAR_CHART'     // 柱状图
  | 'LINE_CHART'    // 折线图
  | 'PIE_CHART'     // 饼图
  | 'AREA_CHART'    // 面积图
  | 'SCATTER_CHART' // 散点图
  | 'HEATMAP'       // 热力图
  | 'TIMELINE'      // 时间线
  | 'COMPARISON'    // 对比视图

/** 图表配置 */
export interface ChartConfig {
  /** X轴字段名 */
  xAxis: string
  /** Y轴字段名 */
  yAxis: string
  /** 系列字段名(用于多系列图表) */
  series?: string
  /** 图表标题 */
  title?: string
  /** 其他配置选项 */
  options?: Record<string, unknown>
}

/** 数据特征 */
export interface DataCharacteristics {
  /** 是否包含时间列 */
  hasTimeColumn: boolean
  /** 是否包含数值列 */
  hasNumericColumn: boolean
  /** 是否为聚合查询结果 */
  isAggregation: boolean
  /** 是否为单个值 */
  isSingleValue: boolean
  /** 行数 */
  rowCount: number
  /** 列数 */
  columnCount: number
  /** 数值列列表 */
  numericColumns: string[]
  /** 分类列列表 */
  categoricalColumns: string[]
  /** 时间列名称 */
  timeColumn?: string
}

/** 可视化配置 */
export interface VisualizationConfig {
  /** 主要渲染类型(AI推荐的最佳展示方式) */
  primaryType: VisualizationType
  /** 备选渲染类型(用户可切换) */
  alternativeTypes: VisualizationType[]
  /** 图表配置(如果是图表类型) */
  chartConfig?: ChartConfig
  /** 数据特征(辅助前端决策) */
  characteristics: DataCharacteristics
  /** AI推荐理由 */
  reason?: string
}
