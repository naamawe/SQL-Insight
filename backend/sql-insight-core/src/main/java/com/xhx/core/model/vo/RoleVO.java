package com.xhx.core.model.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 角色视图对象：用于前端页面显示
 * @author master
 */
@Data
public class RoleVO {

    private Long id;

    private String roleName;

    private String description;

    private LocalDateTime gmtCreated;

    private LocalDateTime gmtModified;
}