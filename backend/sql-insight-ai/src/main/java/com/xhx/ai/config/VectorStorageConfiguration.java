package com.xhx.ai.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author master
 */
@Configuration
@EnableConfigurationProperties({QdrantProperties.class, AliyunAiProperties.class})
public class VectorStorageConfiguration {

    @Bean(destroyMethod = "close")
    public QdrantClient qdrantClient(QdrantProperties properties) {
        // 关闭 tls
        QdrantGrpcClient grpcClient = QdrantGrpcClient.newBuilder(
                        properties.getHost(),
                        properties.getPort(),
                        false)
                .build();

        return new QdrantClient(grpcClient);
    }
}