package com.xhx.core.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;
import java.util.Map;

/**
 * 聊天记录完成事件
 * <p>当 AI 对话完成，需要缓存查询结果和保存图表配置时发布</p>
 * @author master
 */
@Getter
public class ChatRecordCompletedEvent extends ApplicationEvent {

    private final Long recordId;
    private final List<Map<String, Object>> data;
    private final String summary;

    public ChatRecordCompletedEvent(Object source, Long recordId,
                                    List<Map<String, Object>> data, String summary) {
        super(source);
        this.recordId = recordId;
        this.data = data;
        this.summary = summary;
    }
}