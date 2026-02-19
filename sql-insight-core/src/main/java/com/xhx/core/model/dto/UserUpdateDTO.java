package com.xhx.core.model.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * 用户更新对象：管理员修改用户角色或状态时使用
 * @author master
 */
@Data
public class UserUpdateDTO {

    @NotNull(message = "用户 ID 不能为空")
    private Long id;

    @NotNull(message = "角色不能为空")
    private Long roleId;

    @Pattern(
            regexp = "^(SUPER_ADMIN|ADMIN|USER)$",
            message = "系统权限值非法，只允许 SUPER_ADMIN / ADMIN / USER"
    )
    private String systemPermission;

    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态值非法")
    @Max(value = 1, message = "状态值非法")
    private Short status;
}