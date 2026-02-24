package com.xhx.core.model.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户视图对象：用于前端页面显示
 * @author master
 */
@Data
public class UserVO {
    private Long id;
    
    private String userName;
    
    private Long roleId;
    
    /** 角色名称 */
    private String roleName;

    private String systemPermission;
    
    /** 状态：1-正常，0-禁用 */
    private Short status;
    
    private LocalDateTime gmtCreated;
}
