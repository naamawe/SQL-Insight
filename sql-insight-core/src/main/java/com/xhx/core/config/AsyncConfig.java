package com.xhx.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;

import java.util.concurrent.Executor;

/**
 * @author master
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "aiExecutor")
    public Executor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ai-chat-");
        executor.setTaskDecorator(DelegatingSecurityContextRunnable::new);
        executor.initialize();
        return executor;
    }
}