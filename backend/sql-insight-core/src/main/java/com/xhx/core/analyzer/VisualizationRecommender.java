package com.xhx.core.analyzer;

import com.xhx.core.model.dto.visualization.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 可视化推荐器
 * 优先使用 AI 推荐，失败时降级到规则推荐
 * @author master
 */
@Slf4j
@Component
public class VisualizationRecommender {

    @Autowired(required = false)
    private AiVisualizationRecommender aiRecommender;

    /**
     * 推荐可视化配置
     * 优先使用 AI 推荐，失败时降级到规则推荐
     * @param userQuestion 用户问题
     * @param characteristics 数据特征
     * @param data 原始数据
     * @param sql SQL语句(用于辅助判断)
     * @return 可视化配置
     */
    public VisualizationConfig recommend(String userQuestion,
                                         DataCharacteristics characteristics,
                                         List<Map<String, Object>> data,
                                         String sql) {

        log.info("========== 开始推荐可视化方案 ==========");

        // 优先使用 AI 推荐
        if (aiRecommender != null) {
            try {
                log.info("使用 AI 推荐引擎");
                VisualizationConfig aiConfig = aiRecommender.recommend(userQuestion, sql, data);
                log.info("AI 推荐成功: {}", aiConfig.getPrimaryType());
                return aiConfig;
            } catch (Exception e) {
                log.warn("AI 推荐失败，降级到规则推荐: {}", e.getMessage());
            }
        } else {
            log.info("AI 推荐器未配置，使用规则推荐");
        }

        // 降级到规则推荐
        return recommendByRules(characteristics, data);
    }

    /**
     * 基于规则的推荐（降级方案）
     */
    private VisualizationConfig recommendByRules(DataCharacteristics characteristics,
                                                  List<Map<String, Object>> data) {
        log.info("数据特征: 行数={}, 列数={}, 有时间列={}, 有数值列={}, 是聚合={}, 是单值={}",
                characteristics.getRowCount(),
                characteristics.getColumnCount(),
                characteristics.getHasTimeColumn(),
                characteristics.getHasNumericColumn(),
                characteristics.getIsAggregation(),
                characteristics.getIsSingleValue());
        log.info("时间列: {}", characteristics.getTimeColumn());
        log.info("数值列: {}", characteristics.getNumericColumns());
        log.info("分类列: {}", characteristics.getCategoricalColumns());

        VisualizationConfig config;

        // 规则1: 单个值 -> METRIC
        if (characteristics.getIsSingleValue()) {
            log.info("匹配规则1: 单个值 -> METRIC");
            config = buildMetricConfig(characteristics, data);
        }
        // 规则2: 少量记录(≤10行) 且 列数适中(2-5列) -> CARDS
        else if (characteristics.getRowCount() <= 10
            && characteristics.getColumnCount() >= 2
            && characteristics.getColumnCount() <= 5) {
            log.info("匹配规则2: 少量记录 -> CARDS");
            config = buildCardsConfig(characteristics, data);
        }
        // 规则3: 有时间列 + 数值列 -> LINE_CHART
        else if (characteristics.getHasTimeColumn()
            && characteristics.getHasNumericColumn()) {
            log.info("匹配规则3: 时间序列 -> LINE_CHART");
            config = buildLineChartConfig(characteristics, data);
        }
        // 规则4: 聚合查询 + 分类列 + 数值列 -> BAR_CHART
        else if (characteristics.getIsAggregation()
            && !characteristics.getCategoricalColumns().isEmpty()
            && !characteristics.getNumericColumns().isEmpty()) {
            log.info("匹配规则4: 分类聚合 -> BAR_CHART");
            config = buildBarChartConfig(characteristics, data);
        }
        // 规则5: 默认 -> TABLE
        else {
            log.info("匹配规则5: 默认 -> TABLE");
            config = buildTableConfig(characteristics, data);
        }

        log.info("推荐结果: 主类型={}, 备选类型={}",
                config.getPrimaryType(),
                config.getAlternativeTypes());
        if (config.getChartConfig() != null) {
            log.info("图表配置: X轴={}, Y轴={}, 标题={}",
                    config.getChartConfig().getXAxis(),
                    config.getChartConfig().getYAxis(),
                    config.getChartConfig().getTitle());
        }
        log.info("========== 可视化推荐完成 ==========");

        return config;
    }

    /**
     * 构建 METRIC 配置
     */
    private VisualizationConfig buildMetricConfig(DataCharacteristics characteristics, 
                                                   List<Map<String, Object>> data) {
        return VisualizationConfig.builder()
                .primaryType(VisualizationType.METRIC)
                .alternativeTypes(Arrays.asList(VisualizationType.TABLE))
                .characteristics(characteristics)
                .reason("查询返回单个统计值，使用大数字指标卡展示更直观")
                .build();
    }

    /**
     * 构建 CARDS 配置
     */
    private VisualizationConfig buildCardsConfig(DataCharacteristics characteristics, 
                                                  List<Map<String, Object>> data) {
        return VisualizationConfig.builder()
                .primaryType(VisualizationType.CARDS)
                .alternativeTypes(Arrays.asList(VisualizationType.TABLE))
                .characteristics(characteristics)
                .reason("查询返回少量记录，使用卡片列表展示更美观")
                .build();
    }

    /**
     * 构建 LINE_CHART 配置
     */
    private VisualizationConfig buildLineChartConfig(DataCharacteristics characteristics, 
                                                      List<Map<String, Object>> data) {
        String timeColumn = characteristics.getTimeColumn();
        String numericColumn = characteristics.getNumericColumns().get(0);

        ChartConfig chartConfig = ChartConfig.builder()
                .xAxis(timeColumn)
                .yAxis(numericColumn)
                .title("时间趋势图")
                .build();

        return VisualizationConfig.builder()
                .primaryType(VisualizationType.LINE_CHART)
                .alternativeTypes(Arrays.asList(
                    VisualizationType.TABLE
                ))
                .chartConfig(chartConfig)
                .characteristics(characteristics)
                .reason("查询包含时间序列数据，使用折线图展示趋势变化")
                .build();
    }

    /**
     * 构建 BAR_CHART 配置
     */
    private VisualizationConfig buildBarChartConfig(DataCharacteristics characteristics, 
                                                     List<Map<String, Object>> data) {
        String categoryColumn = characteristics.getCategoricalColumns().get(0);
        String numericColumn = characteristics.getNumericColumns().get(0);

        ChartConfig chartConfig = ChartConfig.builder()
                .xAxis(categoryColumn)
                .yAxis(numericColumn)
                .title("分类对比图")
                .build();

        return VisualizationConfig.builder()
                .primaryType(VisualizationType.BAR_CHART)
                .alternativeTypes(Arrays.asList(
                    VisualizationType.PIE_CHART, 
                    VisualizationType.TABLE
                ))
                .chartConfig(chartConfig)
                .characteristics(characteristics)
                .reason("查询按分类统计数据，使用柱状图展示对比关系")
                .build();
    }

    /**
     * 构建 TABLE 配置
     */
    private VisualizationConfig buildTableConfig(DataCharacteristics characteristics, 
                                                  List<Map<String, Object>> data) {
        return VisualizationConfig.builder()
                .primaryType(VisualizationType.TABLE)
                .alternativeTypes(new ArrayList<>())
                .characteristics(characteristics)
                .reason("查询返回复杂数据或大量记录，使用表格展示更合适")
                .build();
    }
}
