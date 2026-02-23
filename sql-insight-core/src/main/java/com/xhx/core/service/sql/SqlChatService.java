package com.xhx.core.service.sql;

import com.xhx.core.model.dto.SqlChatResponse;

/**
 * SQL 对话服务接口
 * @author master
 */
public interface SqlChatService {

    /**
     * 正常对话：生成 SQL → 执行 → 生成自然语言摘要
     */
    SqlChatResponse chat(Long userId, Long sessionId, String question);

    /**
     * 自动纠错：重新生成 SQL → 执行 → 生成自然语言摘要
     */
    SqlChatResponse correct(Long userId, Long sessionId, String errorMessage, String wrongSql);
}