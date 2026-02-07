package com.xhx.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author master
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SqlChatResponse {
    private Long sessionId;
    private String sql;
}