package com.xhx.core.service.sql;

/**
 * @author master
 */
public interface SqlGeneratorService {

    /**
     * 生成SQL
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param question  问题
     * @return  SQL
     */
    String generate(Long userId, Long sessionId, String question);
}
