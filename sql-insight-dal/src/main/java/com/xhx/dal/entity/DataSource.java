package com.xhx.dal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author master
 */
@Data
@TableName("data_source")
public class DataSource {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String connName;
    private String dbType;
    private String driverClassName;
    private String host;
    private Integer port;
    private String username;
    private String password;
    private String databaseName;
    private LocalDateTime gmtCreated;

    public String toJdbcUrl() {
        return String.format(
                "jdbc:%s://%s:%d/%s?useInformationSchema=true&remarks=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai",
                dbType.toLowerCase(), host, port, databaseName
        );
    }

}