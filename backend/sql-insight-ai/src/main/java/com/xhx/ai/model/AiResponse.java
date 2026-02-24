package com.xhx.ai.model;

public record AiResponse(
    String rawText,    // AI 原始返回
    String cleanSql,   // 提取后的纯净 SQL
    boolean isExplain  // 是否命中 [EXPLAIN] 逻辑
) {}