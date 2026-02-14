package com.xhx.core.service.sql;

import java.util.List;
import java.util.Map;

public interface SqlExecutorService {
    /**
     * 执行 SQL 并返回结果
     * @param dataSourceId 目标数据源 ID
     * @param sql 经过校验的安全 SQL
     * @return 结果集
     */
    List<Map<String, Object>> execute(Long dataSourceId, String sql);
}
