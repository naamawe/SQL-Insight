package com.xhx.core.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录/注册 DTO
 * @author master
 */
@Data
public class LoginDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 32, message = "用户名长度必须在 4-32 位之间")
    private String username;

    /**
     * 密码规则：8-64位，必须包含字母和数字
     * 登录场景不做强度校验（兼容旧密码），注册场景 @Valid 会触发
     * 用同一个 DTO 时，注册接口加 @Valid，登录接口不加即可绕过
     * 但这里统一加上，因为合法的老密码也符合该规则
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度必须在 8-64 位之间")
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
            message = "密码必须同时包含字母和数字"
    )
    private String password;
}