package com.xhx.core.model.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 聊天会话视图对象
 * @author master
 */
@Data
public class ChatSessionVO {
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 对应的数据源配置ID */
    private Long dataSourceId;

    /** 数据源名称 */
    private String dataSourceName;

    /** 对话标题 */
    private String title;

    private LocalDateTime createTime;
}
