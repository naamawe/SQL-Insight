package com.xhx.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xhx.dal.entity.UserDataSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author master
 */
@Mapper
public interface UserDataSourceMapper extends BaseMapper<UserDataSource> {
    /**
     * 根据数据源 ID 查询用户 ID
     * @param dsId 数据源 ID
     * @return 用户 ID 列表
     */
    @Select("SELECT user_id FROM user_data_source WHERE data_source_id = #{dsId}")
    List<Long> selectUserIdsByDataSourceId(Long dsId);
}