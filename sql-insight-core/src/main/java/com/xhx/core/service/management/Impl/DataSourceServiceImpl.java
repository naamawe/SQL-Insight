package com.xhx.core.service.management.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhx.common.context.UserContext;
import com.xhx.common.exception.ConnectionException;
import com.xhx.common.exception.NotExistException;
import com.xhx.common.exception.ServiceException;
import com.xhx.core.service.management.DataSourceService;
import com.xhx.dal.config.DynamicDataSourceManager;
import com.xhx.dal.entity.DataSource;
import com.xhx.dal.entity.TablePermission;
import com.xhx.dal.entity.UserDataSource;
import com.xhx.dal.mapper.DataSourceMapper;
import com.xhx.dal.mapper.TablePermissionMapper;
import com.xhx.dal.mapper.UserDataSourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author master
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataSourceServiceImpl implements DataSourceService {

    private final DataSourceMapper dataSourceMapper;
    private final UserDataSourceMapper userDataSourceMapper;
    private final DynamicDataSourceManager dynamicDataSourceManager;
    private final TablePermissionMapper tablePermissionMapper;

    @Override
    public void testConnection(DataSource ds) {
        try (Connection conn = DriverManager.getConnection(
                ds.toJdbcUrl(), ds.getUsername(), ds.getPassword())) {
            if (conn == null || conn.isClosed()) {
                throw new SQLException("连接建立失败");
            }
        } catch (SQLException e) {
            log.error("数据源 [{}] 连接测试失败: {}", ds.getConnName(), e.getMessage());
            throw new ConnectionException("连接失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addDataSource(DataSource ds) {
        // 测试连接
        this.testConnection(ds);
        dataSourceMapper.insert(ds);

        // 默认将该数据源授权给当前创建者
        UserDataSource uds = new UserDataSource();
        uds.setUserId(UserContext.getUserId());
        uds.setDataSourceId(ds.getId());
        userDataSourceMapper.insert(uds);

        log.info("==> 数据源 [{}] 已保存，ID: {}, 已授权给用户: {}",
                ds.getConnName(), ds.getId(), uds.getUserId());
    }

    @Override
    public List<String> getTableNames(Long id) {
        // 获取数据库连接配置
        DataSource ds = dataSourceMapper.selectById(id);
        if (ds == null) {
            throw new NotExistException("数据源不存在");
        }

        List<String> tables = new ArrayList<>();

        // 获取连接池里的连接
        javax.sql.DataSource pooledDs = dynamicDataSourceManager.getDataSource(ds);

        try (Connection conn = pooledDs.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            try (ResultSet rs = metaData.getTables(ds.getDatabaseName(), null, null, new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    tables.add(tableName);
                }
            }
            log.info("==> 成功从数据源 {} 同步到 {} 张表", ds.getConnName(), tables.size());
        } catch (SQLException e) {
            log.error("==> 获取元数据失败: {}", e.getMessage());
            throw new ServiceException("同步数据库表结构失败: " + e.getMessage());
        }

        Collections.sort(tables);
        return tables;
    }

    @Override
    public Page<DataSource> getDataSourcePage(int current, int size, String connName) {
        Page<DataSource> page = new Page<>(current, size);

        LambdaQueryWrapper<DataSource> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(connName)) {
            wrapper.like(DataSource::getConnName, connName);
        }
        wrapper.select(DataSource.class, info -> !"password".equals(info.getColumn()));
        wrapper.orderByDesc(DataSource::getGmtCreated);

        return dataSourceMapper.selectPage(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDataSource(DataSource ds) {
        this.testConnection(ds);

        dataSourceMapper.updateById(ds);
        dynamicDataSourceManager.removeDataSource(ds.getId());

        log.info("==> 数据源 [{}] 配置已更新，旧连接池已释放", ds.getConnName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDataSource(Long id) {
        DataSource ds = dataSourceMapper.selectById(id);
        if (ds == null) {
            return;
        }

        dynamicDataSourceManager.removeDataSource(id);

        userDataSourceMapper.delete(new LambdaQueryWrapper<UserDataSource>()
                .eq(UserDataSource::getDataSourceId, id));
        tablePermissionMapper.delete(new LambdaQueryWrapper<TablePermission>()
                .eq(TablePermission::getDataSourceId, id));

        dataSourceMapper.deleteById(id);
        log.info("==> 数据源 [{}] 已执行软删除，物理连接已释放", ds.getConnName());
    }
}
