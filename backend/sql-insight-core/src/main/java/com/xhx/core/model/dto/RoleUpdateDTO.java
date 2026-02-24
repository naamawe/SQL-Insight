package com.xhx.core.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 角色修改 DTO
 * @author master
 */
@Data
public class RoleUpdateDTO {

    @NotNull(message = "角色 ID 不能为空")
    private Long id;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 50, message = "角色名称不能超过 50 个字符")
    private String roleName;

    @Size(max = 200, message = "描述不能超过 200 个字符")
    private String description;
}