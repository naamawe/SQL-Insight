package com.xhx.core.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 数据源删除事件（删除数据源时发布，失效相关表名缓存）
 * @author master
 */
@Getter
public class DataSourceDeletedEvent extends ApplicationEvent {

    private final Long dataSourceId;

    public DataSourceDeletedEvent(Object source, Long dataSourceId) {
        super(source);
        this.dataSourceId = dataSourceId;
    }
}