package com.xhx.dal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @author master
 */
@Data
@TableName("query_policy")
public class QueryPolicy {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long roleId;
    private Short allowJoin;
    private Short allowSubquery;
    private Integer maxLimit;
    private Short allowAggregation;
}
