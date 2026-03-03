package com.xhx.core.analyzer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhx.core.model.dto.visualization.*;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * AI 驱动的可视化推荐器
 * 使用 LLM 智能分析数据特征，推荐最佳可视化方案
 *
 * 优势：
 * 1. 不依赖字段名，理解数据语义
 * 2. 考虑用户问题意图
 * 3. 适用于任何数据库
 *
 * @author master
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiVisualizationRecommender {

    private final ChatLanguageModel chatLanguageModel;
    private final ObjectMapper objectMapper;

    /** 传给 AI 的最大样本行数 */
    private static final int MAX_SAMPLE_ROWS = 3;

    private static final String SYSTEM_PROMPT = """
            你是一个数据可视化专家，擅长根据数据特征推荐最佳的可视化方案。

            可用的图表类型：
            - METRIC: 大数字指标卡（适合单个统计值）
            - CARDS: 卡片列表（适合少量记录，2-10条）
            - TABLE: 表格（适合复杂数据或大量记录）
            - BAR_CHART: 柱状图（适合分类对比）
            - LINE_CHART: 折线图（适合时间趋势）
            - PIE_CHART: 饼图（适合占比分析）
            - AREA_CHART: 面积图（适合时间趋势+累积）

            分析原则：
            1. 优先考虑数据的统计特征（唯一值数量、数据类型、分布）
            2. 不要依赖字段名判断，要看实际数据内容
            3. 选择最能突出数据特点的图表类型
            4. 为图表选择合适的 X 轴和 Y 轴字段

            字段选择规则：
            - X 轴：选择分类维度或时间维度（唯一值适中的字段）
            - Y 轴：选择数值指标（可以计算、对比的字段）
            - 跳过 ID 字段（如 id, user_id 等）
            - 跳过敏感字段（如 password, token 等）

            请以 JSON 格式返回推荐结果，格式如下：
            {
              "primaryType": "BAR_CHART",
              "alternativeTypes": ["PIE_CHART", "TABLE"],
              "xAxis": "category_field",
              "yAxis": "value_field",
              "reason": "推荐理由（30字以内）"
            }
            """;

    /**
     * 使用 AI 推荐可视化方案
     */
    public VisualizationConfig recommend(String userQuestion,
                                         String sql,
                                         List<Map<String, Object>> data) {
        try {
            log.info("========== AI 可视化推荐开始 ==========");

            // 构建用户提示词
            String userPrompt = buildUserPrompt(userQuestion, sql, data);
            log.debug("AI 提示词: {}", userPrompt);

            // 调用 AI
            Response<AiMessage> response = chatLanguageModel.generate(
                SystemMessage.from(SYSTEM_PROMPT),
                UserMessage.from(userPrompt)
            );

            String aiResponse = response.content().text();
            log.info("AI 原始响应: {}", aiResponse);

            // 解析 AI 响应
            VisualizationConfig config = parseAiResponse(aiResponse, data);

            log.info("AI 推荐结果: 主类型={}, X轴={}, Y轴={}",
                    config.getPrimaryType(),
                    config.getChartConfig() != null ? config.getChartConfig().getXAxis() : "N/A",
                    config.getChartConfig() != null ? config.getChartConfig().getYAxis() : "N/A");
            log.info("========== AI 可视化推荐完成 ==========");

            return config;

        } catch (Exception e) {
            log.error("AI 推荐失败，降级到规则推荐", e);
            return buildFallbackConfig(data);
        }
    }

    /**
     * 构建用户提示词
     */
    private String buildUserPrompt(String userQuestion, String sql, List<Map<String, Object>> data) {
        if (data == null || data.isEmpty()) {
            return String.format("""
                    用户问题: %s
                    SQL: %s
                    查询结果: 空（0 行）

                    请推荐可视化方案。
                    """, userQuestion, sql);
        }

        // 获取数据样本
        int sampleSize = Math.min(MAX_SAMPLE_ROWS, data.size());
        List<Map<String, Object>> sample = data.subList(0, sampleSize);

        // 分析字段特征
        Map<String, Object> firstRow = data.get(0);
        StringBuilder fieldAnalysis = new StringBuilder();

        for (String field : firstRow.keySet()) {
            // 统计唯一值数量
            Set<Object> uniqueValues = new HashSet<>();
            for (Map<String, Object> row : data) {
                Object value = row.get(field);
                if (value != null) {
                    uniqueValues.add(value);
                }
            }

            Object sampleValue = firstRow.get(field);
            String valueType = sampleValue != null ? sampleValue.getClass().getSimpleName() : "null";

            fieldAnalysis.append(String.format("  - %s: 类型=%s, 唯一值数=%d/%d, 示例值=%s\n",
                    field, valueType, uniqueValues.size(), data.size(), sampleValue));
        }

        return String.format("""
                用户问题: %s
                SQL: %s

                数据特征:
                - 总行数: %d
                - 总列数: %d

                字段分析:
                %s

                数据样本（前 %d 行）:
                %s

                请分析数据特征，推荐最佳可视化方案，并选择合适的 X 轴和 Y 轴字段。
                """,
                userQuestion,
                sql,
                data.size(),
                firstRow.size(),
                fieldAnalysis.toString(),
                sampleSize,
                formatSample(sample)
        );
    }

    /**
     * 格式化数据样本
     */
    private String formatSample(List<Map<String, Object>> sample) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sample.size(); i++) {
            sb.append(String.format("第 %d 行: %s\n", i + 1, sample.get(i)));
        }
        return sb.toString();
    }

    /**
     * 解析 AI 响应
     */
    private VisualizationConfig parseAiResponse(String aiResponse, List<Map<String, Object>> data) {
        try {
            // 提取 JSON（AI 可能返回带解释的文本）
            String json = extractJson(aiResponse);

            // 解析 JSON
            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(json, Map.class);

            String primaryType = (String) result.get("primaryType");
            @SuppressWarnings("unchecked")
            List<String> alternativeTypes = (List<String>) result.getOrDefault("alternativeTypes", new ArrayList<>());
            String xAxis = (String) result.get("xAxis");
            String yAxis = (String) result.get("yAxis");
            String reason = (String) result.get("reason");

            // 构建配置
            VisualizationConfig.VisualizationConfigBuilder builder = VisualizationConfig.builder()
                    .primaryType(VisualizationType.valueOf(primaryType))
                    .alternativeTypes(alternativeTypes.stream()
                            .map(VisualizationType::valueOf)
                            .toList())
                    .reason(reason);

            // 如果是图表类型，添加图表配置
            if (xAxis != null && yAxis != null) {
                ChartConfig chartConfig = ChartConfig.builder()
                        .xAxis(xAxis)
                        .yAxis(yAxis)
                        .title(reason)
                        .build();
                builder.chartConfig(chartConfig);
            }

            return builder.build();

        } catch (Exception e) {
            log.error("解析 AI 响应失败: {}", aiResponse, e);
            throw new RuntimeException("解析 AI 响应失败", e);
        }
    }

    /**
     * 从文本中提取 JSON
     */
    private String extractJson(String text) {
        // 尝试找到 JSON 代码块
        int jsonStart = text.indexOf("```json");
        if (jsonStart != -1) {
            int jsonEnd = text.indexOf("```", jsonStart + 7);
            if (jsonEnd != -1) {
                return text.substring(jsonStart + 7, jsonEnd).trim();
            }
        }

        // 尝试找到 { } 包裹的 JSON
        int braceStart = text.indexOf("{");
        int braceEnd = text.lastIndexOf("}");
        if (braceStart != -1 && braceEnd != -1 && braceEnd > braceStart) {
            return text.substring(braceStart, braceEnd + 1);
        }

        // 假设整个文本就是 JSON
        return text.trim();
    }

    /**
     * 降级方案：AI 失败时使用简单规则
     */
    private VisualizationConfig buildFallbackConfig(List<Map<String, Object>> data) {
        if (data == null || data.isEmpty()) {
            return VisualizationConfig.builder()
                    .primaryType(VisualizationType.TABLE)
                    .alternativeTypes(new ArrayList<>())
                    .reason("查询结果为空")
                    .build();
        }

        int rowCount = data.size();
        int columnCount = data.get(0).size();

        // 单值 -> METRIC
        if (rowCount == 1 && columnCount == 1) {
            return VisualizationConfig.builder()
                    .primaryType(VisualizationType.METRIC)
                    .alternativeTypes(Arrays.asList(VisualizationType.TABLE))
                    .reason("单个统计值")
                    .build();
        }

        // 少量记录 -> CARDS
        if (rowCount <= 10 && columnCount >= 2 && columnCount <= 5) {
            return VisualizationConfig.builder()
                    .primaryType(VisualizationType.CARDS)
                    .alternativeTypes(Arrays.asList(VisualizationType.TABLE))
                    .reason("少量记录")
                    .build();
        }

        // 默认 -> TABLE
        return VisualizationConfig.builder()
                .primaryType(VisualizationType.TABLE)
                .alternativeTypes(new ArrayList<>())
                .reason("使用表格展示")
                .build();
    }
}
