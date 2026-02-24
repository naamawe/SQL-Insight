package com.xhx.core.model.dto;

import lombok.Data;

import java.util.List;

/**
 * @author master
 */
@Data
public class PermissionAssignDTO {
    private Long roleId;
    private Long dataSourceId;
    private List<String> tableNames;
}