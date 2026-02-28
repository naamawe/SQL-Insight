package com.xhx.core.model.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * 用户保存对象：管理员手动添加用户时使用
 * @author master
 */
@Data
public class UserSaveDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 32, message = "用户名长度必须在 4-32 位之间")
    private String userName;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度必须在 8-64 位之间")
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
            message = "密码必须同时包含字母和数字"
    )
    private String password;

    @NotNull(message = "角色不能为空")
    private Long roleId;

    @NotBlank(message = "系统权限不能为空")
    @Pattern(
            regexp = "^(SUPER_ADMIN|ADMIN|USER)$",
            message = "系统权限值非法，只允许 SUPER_ADMIN / ADMIN / USER"
    )
    private String systemPermission;
}