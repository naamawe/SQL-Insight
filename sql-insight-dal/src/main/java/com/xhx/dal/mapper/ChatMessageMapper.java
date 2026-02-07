package com.xhx.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xhx.dal.entity.ChatMessageEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author master
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageEntity> {
}