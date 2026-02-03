package com.xhx.dal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @author master
 */
@Data
@TableName("db_config")
public class DbConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String connName;
    private String dbType;
    private String host;
    private Integer port;
    private String username;
    private String password;
    private String databaseName;
}