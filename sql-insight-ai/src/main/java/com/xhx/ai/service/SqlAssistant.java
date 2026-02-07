package com.xhx.ai.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * AI SQL 助手接口
 * @author master
 */
public interface SqlAssistant {

    @SystemMessage(fromResource = "system-prompt.txt")
    String chat(
            @MemoryId Long sessionId,
            @V("schema") String schema,
            @V("policy") String policy,
            @UserMessage String question
    );
}