package com.xhx.common.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author master
 */
public class CommonUtil {


    /**
     * 判断是否为 AI 的解释
     */
    public static boolean isExplain(String sql) {
        if (sql == null || sql.isBlank()) {
            return true;
        }

        String upper = sql.trim().toUpperCase();

        // 命中标签，或者是完全没有 SQL 动词的“大白话”
        return upper.startsWith("[EXPLAIN]") || !CommonUtil.isPossibleSql(upper);
    }

    /**
     * 严谨判断是否具备 SQL 特征
     */
    public static boolean isPossibleSql(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String check = text.trim().toUpperCase();
        return check.matches("^(SELECT|WITH|SHOW|DESC|DESCRIBE|EXPLAIN|CALL|EXEC|EXECUTE|VALUES)\\b.*");
    }


    /**
     * 清理 SQL
     * @param raw 原始 SQL（AI 可能返回单行或多行格式）
     * @return 清理后的 SQL
     */
    public static String cleanSql(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }

        String content = raw.trim();

        // [EXPLAIN] 直接原样返回，不做任何处理
        if (content.startsWith("[EXPLAIN]")) {
            return content;
        }

        // 优先处理 Markdown 代码块（AI 偶尔不听话包了 ```sql）
        Pattern mdPattern = Pattern.compile(
                "```sql\\s*([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE);
        Matcher mdMatcher = mdPattern.matcher(content);
        if (mdMatcher.find()) {
            return finalizeSql(mdMatcher.group(1).trim());
        }

        // 找到 SQL 起始行，向下收集完整语句（一条 SQL 可能多行书写）
        String[] lines = content.split("\\n");
        StringBuilder sqlBuilder = new StringBuilder();
        boolean collecting = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (!collecting) {
                // 找到 SQL 关键字开头的行，开始收集
                if (isSqlKeyword(trimmed) && !trimmed.startsWith("```")) {
                    collecting = true;
                    sqlBuilder.append(trimmed);
                }
            } else {
                // 遇到空行：SQL 结束，停止收集
                if (trimmed.isEmpty()) {
                    break;
                }
                // 遇到自然语言解释：SQL 结束，停止收集
                if (looksLikeNaturalLanguage(trimmed)) {
                    break;
                }
                // 继续拼接当前 SQL 行
                sqlBuilder.append(" ").append(trimmed);
            }
        }

        if (!sqlBuilder.isEmpty()) {
            return finalizeSql(sqlBuilder.toString());
        }

        // 兜底：正则从文本中提取第一段 SQL
        Pattern fallback = Pattern.compile(
                "(SELECT|WITH|SHOW|DESC|DESCRIBE)\\b[\\s\\S]+?(?=\\n\\n|$)",
                Pattern.CASE_INSENSITIVE);
        Matcher fallbackMatcher = fallback.matcher(content);
        if (fallbackMatcher.find()) {
            return finalizeSql(fallbackMatcher.group().trim());
        }

        return content;
    }

    /**
     * 判断是否为 SQL 关键字开头
     */
    private static boolean isSqlKeyword(String line) {
        String upper = line.toUpperCase();
        return upper.startsWith("SELECT") || upper.startsWith("WITH")
                || upper.startsWith("DESC")   || upper.startsWith("SHOW")
                || upper.startsWith("DESCRIBE");
    }

    /**
     * 判断是否为自然语言行（用于终止 SQL 收集）
     * AI 在 SQL 后面跟解释时，解释文本通常有这些特征
     */
    private static boolean looksLikeNaturalLanguage(String line) {
        // 以中文字符开头
        if (line.matches("^[\\u4e00-\\u9fa5].*")) {
            return true;
        }
        // Markdown 格式：加粗、标题、列表
        if (line.startsWith("**") || line.startsWith("##") || line.startsWith("- ")) {
            return true;
        }
        // 常见解释前缀
        if (line.startsWith("Note") || line.startsWith("注意") || line.startsWith("说明")) {
            return true;
        }
        return false;
    }

    /**
     * SQL 最终格式化：合并多余空白，确保以分号结尾
     */
    private static String finalizeSql(String sql) {
        // 去掉单行注释
        String result = sql.replaceAll("--[^\n]*", "")
                .replaceAll("\\s+", " ")
                .trim();
        return result.endsWith(";") ? result : result + ";";
    }
}
