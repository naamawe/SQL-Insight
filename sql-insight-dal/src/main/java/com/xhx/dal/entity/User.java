package com.xhx.dal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author master
 */
@Data
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userName;
    private String password;
    private Long roleId;
    private Short status;
    private LocalDateTime gmtCreated;
    private LocalDateTime gmtModified;
    @TableLogic
    private Integer isDeleted;
}
