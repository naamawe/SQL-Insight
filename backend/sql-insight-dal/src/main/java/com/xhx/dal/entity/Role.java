package com.xhx.dal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author master
 */
@Data
@TableName("role")
public class Role {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String roleName;
    private String description;
    @TableLogic
    private Integer isDeleted;
    private LocalDateTime gmtCreated;
    private LocalDateTime gmtModified;
}
