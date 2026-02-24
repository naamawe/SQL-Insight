package com.xhx.core.model.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 数据源视图对象 - 用于前端展示
 * @author master
 */
@Data
public class DataSourceVO {
    private Long id;
    private String connName;
    private String dbType;
    private String host;
    private Integer port;
    private String databaseName;
    private String username;
    private LocalDateTime gmtCreated;
    private LocalDateTime gmtModified;
}