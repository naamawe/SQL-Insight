package com.xhx.dal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

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
    @TableLogic
    private Integer isDeleted;
    private LocalDateTime gmtCreated;
    private LocalDateTime gmtModified;
}
