package com.xhx.ai.service;

import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

/**
 * 对话记忆存储接口
 * 定义在 ai 模块，实现在 core 模块（依赖 DB Mapper）
 * SqlExecutor 通过此接口读写历史消息，不感知具体存储实现
 * @author master
 */
public interface ChatMemoryStore extends dev.langchain4j.store.memory.chat.ChatMemoryStore {

    @Override
    List<ChatMessage> getMessages(Object memoryId);

    @Override
    void updateMessages(Object memoryId, List<ChatMessage> messages);

    @Override
    void deleteMessages(Object memoryId);
}