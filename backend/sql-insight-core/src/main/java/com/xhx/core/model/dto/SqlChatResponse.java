package com.xhx.core.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * @author master
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SqlChatResponse {
    private Long sessionId;
    private String sql;
    private List<Map<String, Object>> data;
    private Integer total;
    private String summary;
}