package com.xhx.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author master
 */
@Data
@ConfigurationProperties(prefix = "qdrant")
public class QdrantProperties {
    private String host;
    private int port;
    private String collectionName;
}