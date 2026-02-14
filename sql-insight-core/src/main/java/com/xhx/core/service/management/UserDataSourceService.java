package com.xhx.core.service.management;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xhx.dal.entity.UserDataSource;
import java.util.List;

/**
 * @author master
 */
public interface UserDataSourceService extends IService<UserDataSource> {
    
    /**
     * 为用户批量分配数据源权限
     * @param userId 用户ID
     * @param dataSourceIds 数据源ID列表
     */
    void assignDataSources(Long userId, List<Long> dataSourceIds);

    /**
     * 获取用户有权访问的所有数据源ID
     * @param userId 用户ID
     * @return 数据源ID集合
     */
    List<Long> getAuthorizedDataSourceIds(Long userId);
}