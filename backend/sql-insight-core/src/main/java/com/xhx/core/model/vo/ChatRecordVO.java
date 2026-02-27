package com.xhx.core.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * @author master
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRecordVO {

    private Long id;

    private Long sessionId;

    /** 用户原始问题 */
    private String question;

    /** 执行的 SQL */
    private String sqlText;

    /** AI 自然语言摘要 */
    private String summary;

    /** 实际查询总行数 */
    private Integer rowTotal;

    /** 是否经过自动纠错 */
    private Boolean corrected;

    private LocalDateTime createTime;

    /**
     * 查询结果数据（最多 100 行）
     * Redis 缓存有效时返回，过期后为 null
     */
    private List<Map<String, Object>> resultData;

    /**
     * 结果数据是否已过期
     * true 时前端显示"数据已过期，点击重新执行"
     */
    private Boolean resultExpired;
}