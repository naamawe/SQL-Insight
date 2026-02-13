package com.xhx.core.model.dto;

import lombok.Data;

/**
 * @author master
 */
@Data
public class UserPasswordUpdateDTO {
    private String oldPassword;
    private String newPassword;
    private String confirmPassword;
}