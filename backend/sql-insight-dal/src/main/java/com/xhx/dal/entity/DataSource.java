package com.xhx.dal.entity;

import com.baomidou.mybatisplus.annotation.*;
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
    private LocalDateTime gmtModified;
    @TableLogic
    private Integer isDeleted;

    public String toJdbcUrl() {
        return switch (dbType.toLowerCase()) {
            case "mysql" -> String.format(
                    "jdbc:mysql://%s:%d/%s" +
                            "?useInformationSchema=true&remarks=true" +
                            "&characterEncoding=utf8&serverTimezone=Asia/Shanghai" +
                            "&useSSL=false&allowPublicKeyRetrieval=true",
                    host, port, databaseName);

            case "postgresql" -> String.format(
                    "jdbc:postgresql://%s:%d/%s",
                    host, port, databaseName);

            case "sqlserver" -> String.format(
                    "jdbc:sqlserver://%s:%d;databaseName=%s;encrypt=false",
                    host, port, databaseName);

            default -> throw new IllegalArgumentException(
                    "不支持的数据库类型: " + dbType);
        };
    }

}