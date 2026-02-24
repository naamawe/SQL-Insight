package com.xhx.core.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 数据源 Schema 同步事件
 * @author master
 */
@Getter
public class DataSourceSyncEvent extends ApplicationEvent {
    private final Long dataSourceId;

    public DataSourceSyncEvent(Object source, Long dataSourceId) {
        super(source);
        this.dataSourceId = dataSourceId;
    }
}