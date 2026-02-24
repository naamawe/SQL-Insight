package com.xhx.common;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @author master
 */
@Data
@Component
@ConfigurationProperties(prefix = "security.ignore")
public class IgnoreUrlsConfig {
    /**
     * 从配置文件注入的 URL 列表
     */
    private List<String> httpUrls = new ArrayList<>();
}