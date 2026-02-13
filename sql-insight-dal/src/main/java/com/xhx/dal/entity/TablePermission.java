package com.xhx.dal.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author master
 */
@Data
@TableName("table_permission")
public class TablePermission {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long roleId;
    private Long dataSourceId;
    private String tableName;
    private String permission;
    @TableLogic
    private Integer isDeleted;
    private LocalDateTime gmtCreated;
    private LocalDateTime gmtModified;

}
