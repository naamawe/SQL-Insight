package com.xhx.core.model.dto;

import lombok.Data;

/**
 * 用户更新对象：修改角色或状态时使用
 * @author master
 */
@Data
public class UserUpdateDTO {
    private Long id;
    
    private Long roleId;
    
    private Short status;
}
