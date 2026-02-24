package com.xhx.core.service.sql;

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
     * @return 生成的 SQL
     */
    String generate(Long userId, Long sessionId, String question);

    /**
     * Self-correction：将执行错误告知 AI，让其修正 SQL
     *
     * @param userId       用户ID
     * @param sessionId    会话ID
     * @param errorMessage 上一次 SQL 执行时的报错信息
     * @param wrongSql     上一次生成的错误 SQL
     * @return 修正后的 SQL
     */
    String correct(Long userId, Long sessionId, String errorMessage, String wrongSql);
}