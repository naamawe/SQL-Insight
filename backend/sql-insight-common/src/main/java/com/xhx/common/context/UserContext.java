package com.xhx.common.context;

import lombok.Data;

import java.util.List;

/**
 * @author master
 */
public class UserContext {
    private static final ThreadLocal<LoginUser> USER_HOLDER = new ThreadLocal<>();

    public static void setUser(LoginUser user) {
        USER_HOLDER.set(user);
    }

    public static LoginUser getUser() {
        return USER_HOLDER.get();
    }

    public static Long getUserId() {
        return USER_HOLDER.get() != null ? USER_HOLDER.get().getUserId() : null;
    }

    public static void clear() {
        USER_HOLDER.remove();
    }

    @Data
    public static class LoginUser {
        private Long userId;
        private String username;
        private List<String> roles;
    }
}
