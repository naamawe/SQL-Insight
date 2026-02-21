package com.xhx.core.service.sql;

import com.xhx.core.model.ColumnMetadata;
import com.xhx.core.model.TableMetadata;
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
public class SchemaLinker {

    /** 表入选的最低分阈值 */
    private static final int SCORE_THRESHOLD = 5;

    /** 最多保留的表数量（防止 Prompt 过长） */
    private static final int TOP_N = 6;

    /** 字段名匹配单次得分 */
    private static final int SCORE_COLUMN_NAME = 5;

    /** 字段注释匹配单次得分 */
    private static final int SCORE_COLUMN_COMMENT = 3;

    /** 字段名匹配累计上限（避免字段多的大表得分虚高） */
    private static final int SCORE_COLUMN_NAME_CAP = 15;

    /** 字段注释匹配累计上限 */
    private static final int SCORE_COLUMN_COMMENT_CAP = 9;

    /**
     * 从候选表列表中筛选出与用户问题最相关的表
     *
     * @param question       用户原始问题
     * @param candidates     当前用户有权限的所有表元数据
     * @return 过滤并排序后的相关表列表；若无表得分则返回全部候选表（兜底）
     */
    public List<TableMetadata> link(String question, List<TableMetadata> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return candidates;
        }

        // 问题转小写，便于大小写不敏感匹配
        String lowerQuestion = question.toLowerCase();

        // 对每张表评分
        List<ScoredTable> scoredTables = candidates.stream()
                .map(table -> new ScoredTable(table, score(lowerQuestion, table)))
                .filter(st -> st.score > 0)
                .sorted((a, b) -> Integer.compare(b.score, a.score))
                .toList();

        // 兜底：没有任何表得分，返回全部（不能让 AI 拿到空 Schema）
        if (scoredTables.isEmpty()) {
            log.debug("Schema Linking 无匹配，兜底返回全部 {} 张表", candidates.size());
            return candidates;
        }

        // 过滤低分表，保留 TOP_N
        List<TableMetadata> result = scoredTables.stream()
                .filter(st -> st.score >= SCORE_THRESHOLD)
                .limit(TOP_N)
                .map(st -> st.table)
                .collect(Collectors.toList());

        // 如果过滤后为空（所有表分都低于阈值），退化为只取最高分那张
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

    /**
     * 对单张表打分
     */
    private int score(String lowerQuestion, TableMetadata table) {
        int score = 0;

        // 1. 表名匹配（最强信号）
        String tableName = table.getTableName().toLowerCase();
        if (lowerQuestion.contains(tableName)) {
            score += 10;
        }

        // 2. 表注释关键词匹配
        if (table.getTableComment() != null && !table.getTableComment().isBlank()) {
            score += scoreByKeywords(lowerQuestion, table.getTableComment(), 8);
        }

        // 3. 字段名匹配（累加，有上限）
        if (table.getColumns() != null) {
            int columnNameScore = 0;
            int columnCommentScore = 0;

            for (ColumnMetadata col : table.getColumns()) {
                // 字段名匹配
                if (columnNameScore < SCORE_COLUMN_NAME_CAP) {
                    String colName = col.getName().toLowerCase();
                    // 字段名超过2个字符才参与匹配，避免 id/at 这种短字段误匹配
                    if (colName.length() > 2 && lowerQuestion.contains(colName)) {
                        columnNameScore = Math.min(
                                columnNameScore + SCORE_COLUMN_NAME,
                                SCORE_COLUMN_NAME_CAP);
                    }
                }

                // 字段注释关键词匹配
                if (columnCommentScore < SCORE_COLUMN_COMMENT_CAP
                        && col.getComment() != null
                        && !col.getComment().isBlank()
                        && !"(未命名注释)".equals(col.getComment())) {
                    columnCommentScore = Math.min(
                            columnCommentScore + scoreByKeywords(lowerQuestion, col.getComment(), SCORE_COLUMN_COMMENT),
                            SCORE_COLUMN_COMMENT_CAP);
                }
            }

            score += columnNameScore + columnCommentScore;
        }

        return score;
    }

    /**
     * 将文本按空格/标点分词后，逐词检查是否出现在问题中
     * 任意一个词命中即返回 hitScore
     */
    private int scoreByKeywords(String lowerQuestion, String text, int hitScore) {
        // 按空格、下划线、中文标点等分词
        String[] words = text.toLowerCase().split("[\\s，。、：；！？,.!?_]+");
        for (String word : words) {
            if (word.length() > 1 && lowerQuestion.contains(word)) {
                return hitScore;
            }
        }
        return 0;
    }

    /** 内部数据类，表 + 得分 */
    private record ScoredTable(TableMetadata table, int score) {}
}