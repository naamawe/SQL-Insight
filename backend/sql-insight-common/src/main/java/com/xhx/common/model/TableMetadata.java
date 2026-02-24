package com.xhx.common.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据库表元数据模型
 * @author master
 */
@Data
@NoArgsConstructor
public class TableMetadata {
    private String tableName;
    private String tableComment;
    private List<ColumnMetadata> columns;

    /**
     * 将对象转化为 AI 最易理解的描述文本
     */
    public String toPromptString() {
        StringBuilder sb = new StringBuilder();
        sb.append("表名: ").append(tableName);
        if (tableComment != null && !tableComment.isEmpty()) {
            sb.append(" (").append(tableComment).append(")");
        }
        sb.append("\n字段列表:\n");

        for (ColumnMetadata col : columns) {
            sb.append("- ").append(col.toString()).append("\n");
        }

        // 提取主键汇总
        String pks = columns.stream()
                .filter(ColumnMetadata::isPrimaryKey)
                .map(ColumnMetadata::getName)
                .collect(Collectors.joining(", "));

        if (!pks.isEmpty()) {
            sb.append("主键约束: [").append(pks).append("]\n");
        }

        return sb.toString();
    }
}