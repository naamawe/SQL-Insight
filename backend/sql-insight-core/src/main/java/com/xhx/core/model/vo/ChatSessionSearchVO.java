package com.xhx.core.model.vo;

import lombok.Data;

/**
 * 聊天会话搜索视图对象（用于对话框展示）
 * @author master
 */
@Data
public class ChatSessionSearchVO {

    /** 会话 ID */
    private Long id;

    /** 数据源名称 */
    private String dataSourceName;

    /** 对话标题 */
    private String title;

    /** 创建时间 */
    private String createTime;

    /** 对话条数 */
    private Integer messageCount;
}