package com.xhx.core.service.management.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhx.common.context.UserContext;
import com.xhx.common.exception.ConnectionException;
import com.xhx.common.exception.NotExistException;
import com.xhx.common.exception.ServiceException;
import com.xhx.core.event.DataSourceDeletedEvent;
import com.xhx.core.model.dto.DataSourceSaveDTO;
import com.xhx.core.model.dto.DataSourceUpdateDTO;
import com.xhx.core.model.vo.DataSourceVO;
import com.xhx.core.service.cache.CacheService;
import com.xhx.core.service.management.DataSourceService;
import com.xhx.core.service.management.UserDataSourceService;
import com.xhx.dal.config.DynamicDataSourceManager;
import com.xhx.dal.entity.DataSource;
import com.xhx.dal.entity.TablePermission;
import com.xhx.dal.entity.UserDataSource;
import com.xhx.dal.mapper.DataSourceMapper;
import com.xhx.dal.mapper.TablePermissionMapper;
import com.xhx.dal.mapper.UserDataSourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final UserDataSourceService userDataSourceService;
    private final CacheService cacheService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 数据库类型 -> 驱动类名映射表
     */
    private static final Map<String, String> DB_DRIVER_MAP = new HashMap<>();
    static {
        DB_DRIVER_MAP.put("mysql", "com.mysql.cj.jdbc.Driver");
        DB_DRIVER_MAP.put("postgresql", "org.postgresql.Driver");
        DB_DRIVER_MAP.put("sqlserver", "com.microsoft.sqlserver.jdbc.SQLServerDriver");
    }

    @Override
    public void testConnection(DataSourceSaveDTO saveDto) {
        DataSource temp = convertToEntity(saveDto);
        testConnectionInternal(temp);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addDataSource(DataSourceSaveDTO saveDto) {
        // 先测试连接
        DataSource ds = convertToEntity(saveDto);
        testConnectionInternal(ds);

        // 保存到数据库
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
        // 查缓存
        List<String> cached = cacheService.getDsTables(id);
        if (cached != null) {
            log.debug("表名缓存命中，数据源: {}", id);
            return cached;
        }

        // 查目标库
        DataSource ds = dataSourceMapper.selectById(id);
        if (ds == null) {
            throw new NotExistException("数据源不存在");
        }

        List<String> tables = new ArrayList<>();
        javax.sql.DataSource pooledDs = dynamicDataSourceManager.getDataSource(ds);
        try (Connection conn = pooledDs.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getTables(
                    ds.getDatabaseName(), null, null, new String[]{"TABLE"})) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"));
                }
            }
        } catch (SQLException e) {
            throw new ServiceException("同步数据库表结构失败: " + e.getMessage());
        }
        Collections.sort(tables);

        // 回填缓存
        cacheService.putDsTables(id, tables);
        return tables;
    }

    /**
     * 手动刷新表名缓存（管理员在目标库结构变更后调用）
     * 同时失效 Schema 缓存，因为表结构可能发生变化
     */
    @Override
    public List<String> refreshTableNames(Long id) {
        cacheService.evictDsTables(id);
        cacheService.evictSchema(id);
        log.info("数据源 {} 表名缓存和 Schema 缓存已手动清除，下次访问将重新加载", id);
        return getTableNames(id);
    }


    @Override
    public Page<DataSourceVO> getDataSourcePage(int current, int size, String connName) {
        Page<DataSource> page = new Page<>(current, size);

        LambdaQueryWrapper<DataSource> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(connName)) {
            wrapper.like(DataSource::getConnName, connName);
        }
        wrapper.select(DataSource.class, info -> !"password".equals(info.getColumn()));
        wrapper.orderByDesc(DataSource::getGmtCreated);

        Page<DataSource> entityPage = dataSourceMapper.selectPage(page, wrapper);

        Page<DataSourceVO> voPage = new Page<>(current, size, entityPage.getTotal());
        List<DataSourceVO> voList = entityPage.getRecords().stream()
                .map(this::convertToVO)
                .toList();
        voPage.setRecords(voList);

        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDataSource(DataSourceUpdateDTO updateDto) {
        DataSource oldDs = dataSourceMapper.selectById(updateDto.getId());
        if (oldDs == null) {
            throw new NotExistException("数据源不存在");
        }

        // 复制新数据
        DataSource newDs = new DataSource();
        BeanUtils.copyProperties(updateDto, newDs);

        if (!StringUtils.hasText(updateDto.getPassword())) {
            newDs.setPassword(oldDs.getPassword());
        }

        // 自动设置驱动类名
        newDs.setDriverClassName(getDriverClassName(updateDto.getDbType()));

        // 测试新配置
        testConnectionInternal(newDs);

        // 更新数据库
        dataSourceMapper.updateById(newDs);

        // 清除旧连接池
        dynamicDataSourceManager.removeDataSource(newDs.getId());

        log.info("==> 数据源 [{}] 配置已更新，旧连接池已释放", newDs.getConnName());
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

        // 事务提交后异步失效表名缓存
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        eventPublisher.publishEvent(
                                new DataSourceDeletedEvent(this, id));
                    }
                }
        );
    }

    @Override
    public List<DataSourceVO> getAllDataSources() {
        List<DataSource> list = dataSourceMapper.selectList(
                new LambdaQueryWrapper<DataSource>()
                        .select(DataSource.class, info -> !"password".equals(info.getColumn()))
                        .orderByDesc(DataSource::getGmtCreated)
        );

        return list.stream()
                .map(this::convertToVO)
                .toList();
    }

    @Override
    public DataSourceVO getDataSourceById(Long id) {
        DataSource ds = dataSourceMapper.selectOne(
                new LambdaQueryWrapper<DataSource>()
                        .select(DataSource.class, info -> !"password".equals(info.getColumn()))
                        .eq(DataSource::getId, id)
        );

        if (ds == null) {
            throw new NotExistException("数据源不存在");
        }

        return convertToVO(ds);
    }

    @Override
    public List<DataSourceVO> getMyDataSources(Long userId) {
        // 通过 UserDataSourceService 获取（内部已有缓存）
        List<Long> authorizedIds = userDataSourceService.getAuthorizedDataSourceIds(userId);

        if (authorizedIds.isEmpty()) {
            return Collections.emptyList();
        }

        return dataSourceMapper.selectList(
                        new LambdaQueryWrapper<DataSource>()
                                .select(DataSource.class, info -> !"password".equals(info.getColumn()))
                                .in(DataSource::getId, authorizedIds)
                                .orderByDesc(DataSource::getGmtCreated))
                .stream()
                .map(this::convertToVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDeleteDataSources(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        for (Long id : ids) {
            deleteDataSource(id);
        }

        log.info("==> 批量删除数据源成功，共删除 {} 个", ids.size());
    }

    // ========== 私有辅助方法 ==========

    /**
     * 测试连接（内部方法）
     */
    private void testConnectionInternal(DataSource ds) {
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

    /**
     * DTO -> Entity
     */
    private DataSource convertToEntity(DataSourceSaveDTO saveDto) {
        DataSource ds = new DataSource();
        BeanUtils.copyProperties(saveDto, ds);
        ds.setDriverClassName(getDriverClassName(saveDto.getDbType()));
        return ds;
    }

    /**
     * Entity -> VO
     */
    private DataSourceVO convertToVO(DataSource entity) {
        DataSourceVO vo = new DataSourceVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    /**
     * 根据数据库类型自动获取驱动类名
     */
    private String getDriverClassName(String dbType) {
        String driver = DB_DRIVER_MAP.get(dbType.toLowerCase());
        if (driver == null) {
            throw new ServiceException("不支持的数据库类型: " + dbType);
        }
        return driver;
    }
}