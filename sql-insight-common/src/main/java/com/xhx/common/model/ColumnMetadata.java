package com.xhx.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字段元数据模型
 * @author master
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColumnMetadata {

    /** 字段名 */
    private String name;

    /** 字段类型 (如: VARCHAR, INT) */
    private String type;

    /** 字段注释 - AI 理解业务逻辑的核心 */
    private String comment;

    /** 是否为主键 - AI 关联表的关键依据 */
    private boolean primaryKey;

    /** 是否有索引 - AI 优化查询的参考 */
    private boolean indexed;

    /**
     * 优化点：封装语义化描述方法
     * 这种格式比原始对象对 AI 更友好，同时节省 Token
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append(" (").append(type).append(")");

        if (primaryKey) {
            sb.append(" [主键]");
        }
        if (indexed && !primaryKey) {
            sb.append(" [有索引]");
        }

        if (comment != null && !comment.isEmpty()) {
            sb.append(" - ").append(comment);
        } else {
            sb.append(" - (未命名注释)");
        }

        return sb.toString();
    }
}