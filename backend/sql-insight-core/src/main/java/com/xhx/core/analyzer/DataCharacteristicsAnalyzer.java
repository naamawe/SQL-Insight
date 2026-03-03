package com.xhx.core.analyzer;

import com.xhx.core.model.dto.visualization.DataCharacteristics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 数据特征分析器
 * 分析查询结果的数据特征，为可视化推荐提供依据
 * @author master
 */
@Slf4j
@Component
public class DataCharacteristicsAnalyzer {

    /**
     * 分析数据特征
     * @param data 查询结果数据
     * @return 数据特征
     */
    public DataCharacteristics analyze(List<Map<String, Object>> data) {
        if (data == null || data.isEmpty()) {
            return DataCharacteristics.builder()
                    .rowCount(0)
                    .columnCount(0)
                    .hasTimeColumn(false)
                    .hasNumericColumn(false)
                    .isAggregation(false)
                    .isSingleValue(false)
                    .numericColumns(new ArrayList<>())
                    .categoricalColumns(new ArrayList<>())
                    .build();
        }

        Map<String, Object> firstRow = data.get(0);
        Set<String> columns = firstRow.keySet();
        int rowCount = data.size();
        int columnCount = columns.size();

        log.info("========== 开始分析数据特征 ==========");
        log.info("总行数: {}, 总列数: {}", rowCount, columnCount);
        log.info("所有列名: {}", columns);

        List<String> numericColumns = new ArrayList<>();
        List<String> categoricalColumns = new ArrayList<>();
        String timeColumn = null;

        // 分析每一列的类型
        for (String col : columns) {
            Object value = firstRow.get(col);

            if (value == null) {
                log.debug("列 [{}] 值为 null，跳过", col);
                continue;
            }

            // 跳过敏感字段
            if (isSensitiveColumn(col)) {
                log.info("列 [{}] 是敏感字段，跳过", col);
                continue;
            }

            // 跳过ID字段（不适合作为图表轴）
            if (isIdColumn(col)) {
                log.info("列 [{}] 是ID字段，跳过", col);
                continue;
            }

            // 判断时间列
            if (isTimeColumn(col, value)) {
                timeColumn = col;
                log.info("列 [{}] 识别为时间列，值类型: {}", col, value.getClass().getSimpleName());
            }
            // 判断数值列
            else if (isNumericColumn(value)) {
                numericColumns.add(col);
                log.info("列 [{}] 识别为数值列，值类型: {}", col, value.getClass().getSimpleName());
            }
            // 分类列
            else {
                categoricalColumns.add(col);
                log.info("列 [{}] 识别为分类列，值类型: {}", col, value.getClass().getSimpleName());
            }
        }

        log.info("初步分类结果 - 时间列: {}, 数值列: {}, 分类列: {}",
                timeColumn, numericColumns, categoricalColumns);

        // 对分类列按优先级排序（name、title 等优先）
        categoricalColumns.sort(this::compareColumnPriority);
        log.info("分类列排序后: {}", categoricalColumns);

        // 如果没有分类列但有数值列，尝试找出最适合作为标签的数值列
        if (categoricalColumns.isEmpty() && !numericColumns.isEmpty()) {
            log.info("没有分类列，尝试从数值列中找出最适合作为标签的列");
            String bestLabelColumn = findBestLabelColumn(data, numericColumns);
            if (bestLabelColumn != null) {
                numericColumns.remove(bestLabelColumn);
                categoricalColumns.add(bestLabelColumn);
                log.info("选择 [{}] 作为标签列", bestLabelColumn);
            }
        }

        // 判断是否为聚合查询
        boolean isAggregation = isAggregationResult(rowCount, columnCount, numericColumns.size());

        // 判断是否为单个值
        boolean isSingleValue = (rowCount == 1 && columnCount == 1);

        log.info("最终结果 - 时间列: {}, 数值列: {}, 分类列: {}",
                timeColumn, numericColumns, categoricalColumns);
        log.info("是否聚合: {}, 是否单值: {}", isAggregation, isSingleValue);
        log.info("========== 数据特征分析完成 ==========");

        return DataCharacteristics.builder()
                .rowCount(rowCount)
                .columnCount(columnCount)
                .hasTimeColumn(timeColumn != null)
                .hasNumericColumn(!numericColumns.isEmpty())
                .isAggregation(isAggregation)
                .isSingleValue(isSingleValue)
                .numericColumns(numericColumns)
                .categoricalColumns(categoricalColumns)
                .timeColumn(timeColumn)
                .build();
    }

    /**
     * 判断是否为敏感列（需要排除）
     */
    private boolean isSensitiveColumn(String columnName) {
        String lowerName = columnName.toLowerCase();
        return lowerName.matches(".*(password|pwd|token|secret|key|salt|hash).*");
    }

    /**
     * 判断是否为ID列（需要排除，不适合作为图表轴）
     */
    private boolean isIdColumn(String columnName) {
        String lowerName = columnName.toLowerCase();
        // 精确匹配: id, user_id, order_id 等
        // 但不匹配: userid, valid, solid 等
        if (lowerName.equals("id") || lowerName.endsWith("_id")) {
            return true;
        }

        // 检查驼峰命名: userId, orderId (原始字符串中 Id 前有大写字母)
        if (columnName.matches(".*[A-Z][iI]d$")) {
            return true;
        }

        // 检查驼峰命名: idUser, idOrder (原始字符串中 id 后有大写字母)
        if (columnName.matches("^[iI]d[A-Z].*")) {
            return true;
        }

        return false;
    }

    /**
     * 从数值列中找出最适合作为标签的列
     * 策略：选择值的种类数量适中（不太多也不太少）且值不重复的列
     */
    private String findBestLabelColumn(List<Map<String, Object>> data, List<String> numericColumns) {
        if (data.isEmpty() || numericColumns.isEmpty()) {
            return null;
        }

        String bestColumn = null;
        int bestScore = -1;

        for (String col : numericColumns) {
            // 统计该列的唯一值数量
            Set<Object> uniqueValues = new HashSet<>();
            for (Map<String, Object> row : data) {
                Object value = row.get(col);
                if (value != null) {
                    uniqueValues.add(value);
                }
            }

            int uniqueCount = uniqueValues.size();
            int totalCount = data.size();

            // 评分：唯一值数量 = 总行数（每行都不同，适合作为标签）
            // 或者唯一值数量在合理范围内（2-20个）
            int score = 0;
            if (uniqueCount == totalCount) {
                score = 100; // 完美：每行都不同
            } else if (uniqueCount >= 2 && uniqueCount <= 20) {
                score = 50 + uniqueCount; // 还不错：适中的分类数
            }

            if (score > bestScore) {
                bestScore = score;
                bestColumn = col;
            }
        }

        return bestScore > 0 ? bestColumn : null;
    }

    /**
     * 比较列的优先级（用于排序）
     * 优先级：name > title > label > code > 其他
     */
    private int compareColumnPriority(String col1, String col2) {
        int priority1 = getColumnPriority(col1);
        int priority2 = getColumnPriority(col2);
        return Integer.compare(priority1, priority2);
    }

    /**
     * 获取列的优先级分数（越小越优先）
     */
    private int getColumnPriority(String columnName) {
        String lowerName = columnName.toLowerCase();
        if (lowerName.matches(".*(name|username|user_name).*")) return 1;
        if (lowerName.matches(".*(title|subject).*")) return 2;
        if (lowerName.matches(".*(label|tag).*")) return 3;
        if (lowerName.matches(".*(code|no).*")) return 4;
        if (lowerName.matches(".*(type|category|status).*")) return 5;
        return 10; // 其他
    }

    /**
     * 判断是否为时间列
     */
    private boolean isTimeColumn(String columnName, Object value) {
        // 类型判断
        if (value instanceof Date || value instanceof LocalDateTime || value instanceof LocalDate) {
            return true;
        }

        // 列名判断
        String lowerName = columnName.toLowerCase();
        return lowerName.matches(".*(date|time|year|month|day|created|updated).*");
    }

    /**
     * 判断是否为数值列
     */
    private boolean isNumericColumn(Object value) {
        return value instanceof Number || value instanceof BigDecimal;
    }

    /**
     * 判断是否为聚合查询结果
     * 启发式规则: 行数较少 + 包含数值列 = 可能是聚合结果
     */
    private boolean isAggregationResult(int rowCount, int columnCount, int numericColumnCount) {
        // 单行单列且为数值 -> 聚合
        if (rowCount == 1 && columnCount == 1 && numericColumnCount == 1) {
            return true;
        }
        
        // 行数少于20且有数值列 -> 可能是分组聚合
        if (rowCount <= 20 && numericColumnCount > 0) {
            return true;
        }
        
        return false;
    }
}
