package com.xhx.core.service.sql;

import com.xhx.ai.model.AiResponse;

/**
 * @author master
 */
public interface SqlGeneratorService {

    /**
     * 根据用户问题生成 SQL
     *
     * @param userId    用户ID
     * @param sessionId 会话ID
     * @param question  用户问题
     * @return AiResponse，包含 cleanSql 和 isExplain 标志
     */
    String generate(Long userId, Long sessionId, String question);

    /**
     * Self-correction：将执行错误告知 AI，让其修正 SQL
     *
     * @param userId       用户ID
     * @param sessionId    会话ID
     * @param errorMessage 上一次 SQL 执行时的报错信息
     * @param wrongSql     上一次生成的错误 SQL
     * @return AiResponse，包含 cleanSql 和 isExplain 标志
     */
    AiResponse correct(Long userId, Long sessionId, String errorMessage, String wrongSql);
}
