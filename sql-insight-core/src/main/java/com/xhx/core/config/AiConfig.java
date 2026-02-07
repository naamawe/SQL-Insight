package com.xhx.core.config;

import com.xhx.ai.service.SqlAssistant;
import com.xhx.core.service.ChatMemoryStore;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author master
 */
@Configuration
public class AiConfig {

    private static final int MAX_MESSAGES = 10;

    @Resource
    private ChatMemoryStore chatMemoryStore;

    @Bean
    public SqlAssistant sqlAssistant(ChatLanguageModel chatModel) {
        return AiServices.builder(SqlAssistant.class)
                .chatLanguageModel(chatModel)
                .chatMemoryProvider(sessionId -> MessageWindowChatMemory.builder()
                        .id(sessionId)
                        .maxMessages(MAX_MESSAGES)
                        .chatMemoryStore(chatMemoryStore)
                        .build())
                .build();
    }
}
