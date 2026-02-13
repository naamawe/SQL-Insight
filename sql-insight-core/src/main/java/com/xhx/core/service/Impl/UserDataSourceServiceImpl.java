package com.xhx.core.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhx.common.constant.SecurityConstants;
import com.xhx.core.service.UserDataSourceService;
import com.xhx.dal.entity.UserDataSource;
import com.xhx.dal.mapper.UserDataSourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author master
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDataSourceServiceImpl extends ServiceImpl<UserDataSourceMapper, UserDataSource> implements UserDataSourceService {

    private final StringRedisTemplate redisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignDataSources(Long userId, List<Long> dataSourceIds) {
        // 清空旧权限
        this.remove(new LambdaQueryWrapper<UserDataSource>().eq(UserDataSource::getUserId, userId));

        // 插入新权限
        if (dataSourceIds != null && !dataSourceIds.isEmpty()) {
            List<UserDataSource> list = dataSourceIds.stream().map(dsId -> {
                UserDataSource uds = new UserDataSource();
                uds.setUserId(userId);
                uds.setDataSourceId(dsId);
                return uds;
            }).collect(Collectors.toList());
            this.saveBatch(list);
        }

        // 清理数据源 ID 列表缓存
        redisTemplate.delete(SecurityConstants.USER_DATASOURCES_KEY + userId);
        redisTemplate.delete(SecurityConstants.USER_PERMISSION_KEY + userId);

        log.info("==> 用户 {} 权限变更，已清理 Redis 缓存 Key: {}{}",
                userId, SecurityConstants.USER_DATASOURCES_KEY, userId);
    }

    @Override
    public List<Long> getAuthorizedDataSourceIds(Long userId) {
        return this.list(new LambdaQueryWrapper<UserDataSource>()
                .eq(UserDataSource::getUserId, userId))
                .stream()
                .map(UserDataSource::getDataSourceId)
                .collect(Collectors.toList());
    }
}