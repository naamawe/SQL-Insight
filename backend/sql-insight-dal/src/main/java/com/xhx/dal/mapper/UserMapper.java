package com.xhx.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xhx.dal.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author master
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}