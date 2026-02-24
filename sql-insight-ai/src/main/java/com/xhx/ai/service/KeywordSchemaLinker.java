package com.xhx.ai.service;

import com.xhx.common.model.ColumnMetadata;
import com.xhx.common.model.TableMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Schema Linking 组件
 * <p>
 * 根据用户问题从候选表列表中过滤出最相关的表，减少注入 Prompt 的 Schema 噪音。
 * 使用关键词匹配 + 评分机制，不依赖向量数据库，轻量且高效。
 * <p>
 * 评分规则（分值可调）：
 *   - 表名完整出现在问题中          +10
 *   - 表注释关键词出现在问题中       +8
 *   - 字段名出现在问题中            +5  (每个字段独立计算，累加，上限 15)
 *   - 字段注释关键词出现在问题中     +3  (每个字段独立计算，累加，上限 9)
 * <p>
 * 兜底策略：
 *   - 如果没有任何表得分超过阈值，返回全部候选表（防止误过滤导致 AI 无法工作）
 *   - 最多保留 TOP_N 张表，防止权限表过多时 Prompt 仍然过长
 *
 * @author master
 */
@Slf4j
@Component
public class KeywordSchemaLinker implements SchemaLinker{

    private static final int SCORE_THRESHOLD = 5;
    private static final int TOP_N = 6;
    private static final int SCORE_COLUMN_NAME = 5;
    private static final int SCORE_COLUMN_COMMENT = 3;
    private static final int SCORE_COLUMN_NAME_CAP = 15;
    private static final int SCORE_COLUMN_COMMENT_CAP = 9;

    /**
     * 从候选表列表中筛选出与用户问题最相关的表
     *
     * @param question   用户原始问题
     * @param candidates 当前用户有权限的所有表元数据
     * @return 过滤并排序后的相关表列表；若无表得分则返回全部候选表（兜底）
     */
    @Override
    public List<TableMetadata> link(String question, List<TableMetadata> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return candidates;
        }

        String lowerQuestion = question.toLowerCase();

        List<ScoredTable> scoredTables = candidates.stream()
                .map(table -> new ScoredTable(table, score(lowerQuestion, table)))
                .filter(st -> st.score > 0)
                .sorted((a, b) -> Integer.compare(b.score, a.score))
                .toList();

        if (scoredTables.isEmpty()) {
            log.debug("Schema Linking 无匹配，兜底返回全部 {} 张表", candidates.size());
            return candidates;
        }

        List<TableMetadata> result = scoredTables.stream()
                .filter(st -> st.score >= SCORE_THRESHOLD)
                .limit(TOP_N)
                .map(st -> st.table)
                .collect(Collectors.toList());

        if (result.isEmpty()) {
            result = List.of(scoredTables.get(0).table);
        }

        log.info("Schema Linking 完成：候选 {} 张表 → 过滤后 {} 张表，问题: [{}]",
                candidates.size(), result.size(), question);
        if (log.isDebugEnabled()) {
            scoredTables.forEach(st ->
                    log.debug("  表: {}, 得分: {}", st.table.getTableName(), st.score));
        }

        return result;
    }

    private int score(String lowerQuestion, TableMetadata table) {
        int score = 0;

        String tableName = table.getTableName().toLowerCase();
        if (lowerQuestion.contains(tableName)) {
            score += 10;
        }

        if (table.getTableComment() != null && !table.getTableComment().isBlank()) {
            score += scoreByKeywords(lowerQuestion, table.getTableComment(), 8);
        }

        if (table.getColumns() != null) {
            int columnNameScore = 0;
            int columnCommentScore = 0;

            for (ColumnMetadata col : table.getColumns()) {
                if (columnNameScore < SCORE_COLUMN_NAME_CAP) {
                    String colName = col.getName().toLowerCase();
                    if (colName.length() > 2 && lowerQuestion.contains(colName)) {
                        columnNameScore = Math.min(
                                columnNameScore + SCORE_COLUMN_NAME,
                                SCORE_COLUMN_NAME_CAP);
                    }
                }

                if (columnCommentScore < SCORE_COLUMN_COMMENT_CAP
                        && col.getComment() != null
                        && !col.getComment().isBlank()
                        && !"(未命名注释)".equals(col.getComment())) {
                    columnCommentScore = Math.min(
                            columnCommentScore + scoreByKeywords(
                                    lowerQuestion, col.getComment(), SCORE_COLUMN_COMMENT),
                            SCORE_COLUMN_COMMENT_CAP);
                }
            }

            score += columnNameScore + columnCommentScore;
        }

        return score;
    }

    private int scoreByKeywords(String lowerQuestion, String text, int hitScore) {
        String[] words = text.toLowerCase().split("[\\s，。、：；！？,.!?_]+");
        for (String word : words) {
            if (word.length() > 1 && lowerQuestion.contains(word)) {
                return hitScore;
            }
        }
        return 0;
    }

    private record ScoredTable(TableMetadata table, int score) {}
}