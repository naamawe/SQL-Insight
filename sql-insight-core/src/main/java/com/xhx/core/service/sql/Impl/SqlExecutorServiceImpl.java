package com.xhx.core.service.sql.Impl;

import com.xhx.common.exception.NotExistException;
import com.xhx.common.exception.ServiceException;
import com.xhx.core.service.sql.SqlExecutorService;
import com.xhx.dal.config.DynamicDataSourceManager;
import com.xhx.dal.entity.DataSource;
import com.xhx.dal.mapper.DataSourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * @author master
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlExecutorServiceImpl implements SqlExecutorService {

    private final DynamicDataSourceManager dataSourceManager;
    private final DataSourceMapper dataSourceMapper;

    @Override
    public List<Map<String, Object>> execute(Long dataSourceId, String sql) {
        DataSource config = dataSourceMapper.selectById(dataSourceId);
        if (config == null) {
            throw new NotExistException(404, "数据源不存在");
        }

        javax.sql.DataSource ds = dataSourceManager.getDataSource(config);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(ds);

        try {
            log.info("执行目标库 SQL: {}", sql);
            return jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            log.error("SQL 执行失败: {}", e.getMessage());
            throw new ServiceException(500, "目标库执行异常: " + e.getMessage());
        }
    }
}