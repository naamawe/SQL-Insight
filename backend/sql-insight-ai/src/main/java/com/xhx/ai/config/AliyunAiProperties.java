package com.xhx.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author master
 */
@Data
@ConfigurationProperties(prefix = "aliyun.dashscope")
public class AliyunAiProperties {
    private String apiKey;
    private String model;
}