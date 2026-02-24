package com.xhx.core.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 角色权限变更事件（表权限或查询策略变更时发布）
 * @author master
 */
@Getter
public class RolePermissionChangedEvent extends ApplicationEvent {

    private final Long roleId;

    public RolePermissionChangedEvent(Object source, Long roleId) {
        super(source);
        this.roleId = roleId;
    }
}