package com.xhx.core.model;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/**
 * @author master
 */
@Getter
public class LoginUser extends User {

    private final Long userId;
    private final Long roleId;

    public LoginUser(Long userId, Long roleId, String username, String password, Collection<? extends GrantedAuthority> authorities) {
        super(username, password, authorities);
        this.userId = userId;
        this.roleId = roleId;
    }
}

