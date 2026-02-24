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
     * @param raw 原始 SQL
     * @return 清理后的 SQL
     */
    public static String cleanSql(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }

        // 去掉首尾空格
        String content = raw.trim();

        String[] lines = content.split("\\n");
        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty()) {
                continue;
            }
            // 如果这行是 SQL 关键字开头，且不包含 Markdown 标记
            if (isSqlKeyword(trimmedLine) && !trimmedLine.contains("```")) {
                return finalizeSql(trimmedLine);
            }
            break;
        }
        Pattern pattern = Pattern.compile("```sql\\s*([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            String sqlInBlock = matcher.group(1).trim();
            return finalizeSql(sqlInBlock.split(";")[0]);
        }

        Pattern fallbackPattern = Pattern.compile("(SELECT|WITH|SHOW|DESC|UPDATE|INSERT|DELETE)[\\s\\S]+?;?", Pattern.CASE_INSENSITIVE);
        Matcher fallbackMatcher = fallbackPattern.matcher(content);
        if (fallbackMatcher.find()) {
            return finalizeSql(fallbackMatcher.group());
        }
        return content;
    }

    private static boolean isSqlKeyword(String line) {
        String upper = line.toUpperCase();
        return upper.startsWith("SELECT") || upper.startsWith("WITH") ||
                upper.startsWith("DESC")   || upper.startsWith("SHOW");
    }

    private static String finalizeSql(String sql) {
        // 移除单行注释、换行符，并确保以分号结尾
        String result = sql.replaceAll("--.*", "").replaceAll("\\s+", " ").trim();
        return result.endsWith(";") ? result : result + ";";
    }
}
