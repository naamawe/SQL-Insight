package com.xhx.core.model.dto;

import lombok.Data;

/**
 * 用户保存对象：管理员手动添加用户时使用
 * @author master
 */
@Data
public class UserSaveDTO {
    private String userName;
    
    private String password;
    
    private Long roleId;

    private String systemPermission;

    private Short status;
}
