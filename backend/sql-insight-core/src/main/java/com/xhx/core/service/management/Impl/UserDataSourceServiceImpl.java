package com.xhx.core.service.management.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhx.core.service.cache.CacheService;
import com.xhx.core.service.management.UserDataSourceService;
import com.xhx.dal.entity.UserDataSource;
import com.xhx.dal.mapper.UserDataSourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author master
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDataSourceServiceImpl extends ServiceImpl<UserDataSourceMapper, UserDataSource> implements UserDataSourceService {

    private final CacheService cacheService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignDataSources(Long userId, List<Long> dataSourceIds) {
        this.remove(new LambdaQueryWrapper<UserDataSource>()
                .eq(UserDataSource::getUserId, userId));

        if (dataSourceIds != null && !dataSourceIds.isEmpty()) {
            List<UserDataSource> list = dataSourceIds.stream().map(dsId -> {
                UserDataSource uds = new UserDataSource();
                uds.setUserId(userId);
                uds.setDataSourceId(dsId);
                return uds;
            }).toList();
            this.saveBatch(list);
        }

        // 失效缓存（同时失效权限，因为数据源变了权限集合也要重新加载）
        cacheService.evictUserDsIds(userId);
        cacheService.evictUserPermissions(userId);
        cacheService.evictUserPolicy(userId);

        log.info("用户 {} 数据源授权已变更，相关缓存已失效", userId);
    }

    @Override
    public List<Long> getAuthorizedDataSourceIds(Long userId) {
        // 查缓存
        List<Long> cached = cacheService.getUserDsIds(userId);
        if (cached != null) {
            return cached;
        }

        // 查DB
        List<Long> ids = this.list(new LambdaQueryWrapper<UserDataSource>()
                        .eq(UserDataSource::getUserId, userId))
                .stream()
                .map(UserDataSource::getDataSourceId)
                .toList();

        // 回填
        cacheService.putUserDsIds(userId, ids);
        return ids;
    }
}