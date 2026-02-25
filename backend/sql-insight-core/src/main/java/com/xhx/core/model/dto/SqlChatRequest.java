package com.xhx.core.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SSE 流式对话请求体
 * @author master
 */
@Data
public class SqlChatRequest {

    private Long sessionId;

    private Long dataSourceId;

    @NotBlank(message = "问题不能为空")
    @Size(max = 500, message = "问题长度不能超过 500 个字符")
    private String question;
}
