package com.xhx.dal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

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
    private String systemPermission;
}
