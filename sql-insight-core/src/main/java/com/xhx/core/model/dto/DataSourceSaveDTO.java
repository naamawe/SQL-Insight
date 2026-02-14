package com.xhx.core.model.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * 数据源新增DTO
 * @author master
 */
@Data
public class DataSourceSaveDTO {
    
    /** 连接名称（用户友好的别名） */
    @NotBlank(message = "连接名称不能为空")
    @Size(max = 50, message = "连接名称不能超过50个字符")
    private String connName;
    
    /** 数据库类型：mysql / postgresql / oracle 等 */
    @NotBlank(message = "数据库类型不能为空")
    @Pattern(regexp = "^(mysql|postgresql|oracle|sqlserver)$", 
             message = "数据库类型只支持: mysql, postgresql, oracle, sqlserver")
    private String dbType;
    
    /** 主机地址 */
    @NotBlank(message = "主机地址不能为空")
    @Size(max = 100, message = "主机地址不能超过100个字符")
    private String host;
    
    /** 端口号 */
    @NotNull(message = "端口号不能为空")
    @Min(value = 1, message = "端口号必须大于0")
    @Max(value = 65535, message = "端口号不能超过65535")
    private Integer port;
    
    /** 数据库名称 */
    @NotBlank(message = "数据库名称不能为空")
    @Size(max = 64, message = "数据库名称不能超过64个字符")
    private String databaseName;
    
    /** 用户名 */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名不能超过64个字符")
    private String username;
    
    /** 密码 */
    @NotBlank(message = "密码不能为空")
    @Size(max = 128, message = "密码不能超过128个字符")
    private String password;
}