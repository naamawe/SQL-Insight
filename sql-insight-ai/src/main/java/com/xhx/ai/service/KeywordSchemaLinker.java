package com.xhx.ai.service;

import com.xhx.common.model.ColumnMetadata;
import com.xhx.common.model.TableMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 关键词 Schema Linker（轻量实现 / 降级兜底）
 *
 * <p>不依赖任何外部服务，基于词频评分从候选表中筛选相关表，
 * 作为 {@link VectorSchemaLinker} 的降级策略。
 *
 * <p><b>评分规则</b>（累加）：
 * <ul>
 *   <li>表名完整出现在问题中         → +10</li>
 *   <li>表注释关键词命中问题         → +8</li>
 *   <li>字段名命中问题               → +5（累加，上限 15）</li>
 *   <li>字段注释关键词命中问题       → +3（累加，上限 9）</li>
 * </ul>
 *
 * <p><b>结果兜底层次</b>：
 * <ol>
 *   <li>有表达到阈值 → 取 TOP_N 张（最优路径）</li>
 *   <li>有得分但均低于阈值 → 取得分最高的一张（总比没有强）</li>
 *   <li>全部得分为 0 → 返回全量候选（实在没有办法）</li>
 * </ol>
 *
 * @author master
 */
@Slf4j
@Component
public class KeywordSchemaLinker implements SchemaLinker {

    /** 最终筛选的最大表数量 */
    private static final int TOP_N = 6;

    /** 进入最终结果的最低分数线 */
    private static final int SCORE_THRESHOLD = 5;

    // 各维度评分权重
    private static final int W_TABLE_NAME     = 10;
    private static final int W_TABLE_COMMENT  = 8;
    private static final int W_COL_NAME       = 5;
    private static final int W_COL_COMMENT    = 3;

    // 字段级累加上限（防止字段多的大表分数虚高）
    private static final int CAP_COL_NAME    = 15;
    private static final int CAP_COL_COMMENT = 9;

    @Override
    public List<TableMetadata> link(String question, Long dataSourceId,
                                    List<TableMetadata> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        String lq = question.toLowerCase();

        // 计算所有表的得分，过滤掉 0 分表，按分数倒序
        List<ScoredTable> ranked = candidates.stream()
                .map(t -> new ScoredTable(t, score(lq, t)))
                .filter(st -> st.score > 0)
                .sorted(Comparator.comparingInt(ScoredTable::score).reversed())
                .toList();

        // 兜底层次 3：全部为零分，返回全量候选
        if (ranked.isEmpty()) {
            log.debug("[KeywordLinker] 无表得分，返回全量 {} 张表", candidates.size());
            return candidates;
        }

        // 取达到阈值的表，最多 TOP_N 张
        List<TableMetadata> result = ranked.stream()
                .filter(st -> st.score >= SCORE_THRESHOLD)
                .limit(TOP_N)
                .map(ScoredTable::table)
                .collect(Collectors.toList());

        // 兜底层次 2：有得分但均低于阈值，保留最高分一张
        if (result.isEmpty()) {
            result = List.of(ranked.get(0).table());
        }

        log.info("[KeywordLinker] 候选 {} 张 → 筛选后 {} 张，问题: [{}]",
                candidates.size(), result.size(), question);
        return result;
    }

    // ==================== 评分逻辑 ====================

    private int score(String lq, TableMetadata table) {
        int total = 0;

        // 表名：完整命中
        if (lq.contains(table.getTableName().toLowerCase())) {
            total += W_TABLE_NAME;
        }

        // 表注释：任意关键词命中
        if (hasText(table.getTableComment())) {
            total += keywordHit(lq, table.getTableComment(), W_TABLE_COMMENT);
        }

        // 字段级（带上限）
        if (table.getColumns() != null) {
            int colNameAcc    = 0;
            int colCommentAcc = 0;

            for (ColumnMetadata col : table.getColumns()) {
                if (colNameAcc < CAP_COL_NAME) {
                    String cn = col.getName().toLowerCase();
                    // 字段名 ≤ 2 字符容易误匹配（如 id、no），跳过
                    if (cn.length() > 2 && lq.contains(cn)) {
                        colNameAcc = Math.min(colNameAcc + W_COL_NAME, CAP_COL_NAME);
                    }
                }
                if (colCommentAcc < CAP_COL_COMMENT
                        && hasText(col.getComment())
                        && !"(未命名注释)".equals(col.getComment())) {
                    colCommentAcc = Math.min(
                            colCommentAcc + keywordHit(lq, col.getComment(), W_COL_COMMENT),
                            CAP_COL_COMMENT);
                }
            }
            total += colNameAcc + colCommentAcc;
        }

        return total;
    }

    /**
     * 将 text 按标点/空格切词，任意词命中 lq 则返回 hitScore，否则返回 0
     */
    private int keywordHit(String lq, String text, int hitScore) {
        String[] words = text.toLowerCase().split("[\\s，。、：；！？,.!?_\\-]+");
        for (String word : words) {
            if (word.length() > 1 && lq.contains(word)) {
                return hitScore;
            }
        }
        return 0;
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private record ScoredTable(TableMetadata table, int score) {}
}