package com.xhx.dal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

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

}
