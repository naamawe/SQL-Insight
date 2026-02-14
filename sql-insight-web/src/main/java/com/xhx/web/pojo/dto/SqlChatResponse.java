package com.xhx.web.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * @author master
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SqlChatResponse {
    private Long sessionId;
    private String sql;
    private List<Map<String, Object>> data;
}