package com.xhx.core.service.sql;

/**
 * SQL 安全校验服务接口
 * @author master
 */
public interface SqlSecurityService {

    /**
     * 校验生成的 SQL 是否合规
     * @param sql AI 生成的 SQL 语句
     * @param userId 当前用户 ID
     * @param dataSourceId 当前操作的数据源 ID
     * @throws RuntimeException 如果校验不通过，抛出带有具体原因的异常
     */
    void validate(String sql, Long userId, Long dataSourceId);
}
